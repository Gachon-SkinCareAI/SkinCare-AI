import React, { useState } from "react";
import * as Location from "expo-location";
import { Card, Title, T, Button } from "../components/Common";
import {
  ErrorNote,
  Field,
  PolicyLink,
  EmergencyHelp,
} from "../components/ConsentControls";
import ExampleMedicalMap from "../components/ExampleMedicalMap";
import { naverMapSearchUrl } from "../services/maps";

export default function MedicalScreen({
  api,
  kind,
  consent,
  onLogin,
  onConsent,
}) {
  const [type, setType] = useState("HOSPITAL"),
    [region, setRegion] = useState(""),
    [name, setName] = useState("");
  const [result, setResult] = useState(null),
    [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  const lock = React.useRef(false),
    live = React.useRef(true);
  const [example, setExample] = useState(false);
  React.useEffect(() => {
    live.current = true;
    return () => {
      live.current = false;
    };
  }, []);
  async function search(gps) {
    if (lock.current) return;
    lock.current = true;
    setBusy(true);
    setError("");
    setResult(null);
    try {
      let value;
      if (gps) {
        if (kind !== "member" || !consent?.locationGranted)
          throw new Error("가입 후 위치정보 이용에 선택 동의해 주세요.");
        const permission = await Location.requestForegroundPermissionsAsync();
        if (!permission.granted)
          throw new Error(
            "위치 권한을 허용하지 않았어요. 지역명으로 검색할 수 있어요.",
          );
        const position = await Location.getCurrentPositionAsync({
          accuracy: Location.Accuracy.Balanced,
        });
        if (!live.current) return;
        value = await api.post("/api/wc/hospitals/public/nearby", {
          kind: type,
          latitude: position.coords.latitude,
          longitude: position.coords.longitude,
          page: 0,
          size: 20,
        });
      } else {
        if (kind === "anonymous")
          throw new Error(
            "사진 분석으로 비회원 이용을 시작하거나 소셜 로그인 후 검색할 수 있어요.",
          );
        value = await api.request(
          `/api/wc/hospitals/public?kind=${type}&sido=${encodeURIComponent(region.trim())}&name=${encodeURIComponent(name.trim())}&size=20`,
        );
      }
      if (live.current) setResult(value);
    } catch (e) {
      if (live.current) setError(e.message);
    } finally {
      lock.current = false;
      if (live.current) setBusy(false);
    }
  }
  return (
    <>
      <Button secondary disabled={busy} onPress={() => setExample((v) => !v)}>
        {example ? "실제 의료기관 검색으로 돌아가기" : "예시 지도·목록 체험"}
      </Button>
      {example ? (
        <ExampleMedicalMap />
      ) : (
        <>
          <Card>
            <Title>주변 병원·약국</Title>
            <T>
              GPS 검색은 회원가입 시 선택 동의 후 이용해요. 위치를 허용하지
              않아도 지역명으로 검색할 수 있어요.
            </T>
            <Button
              secondary
              disabled={busy}
              onPress={() => {
                setType(type === "HOSPITAL" ? "PHARMACY" : "HOSPITAL");
                setResult(null);
              }}
            >
              {type === "HOSPITAL"
                ? "병원 검색 중 · 약국으로 변경"
                : "약국 검색 중 · 병원으로 변경"}
            </Button>
            {kind === "member" ? (
              consent?.locationGranted ? (
                <Button disabled={busy} onPress={() => search(true)}>
                  현재 위치로 검색
                </Button>
              ) : (
                <Button secondary onPress={onConsent}>
                  개인정보 설정에서 위치 동의
                </Button>
              )
            ) : (
              <Button secondary onPress={onLogin}>
                GPS 검색을 위해 소셜 가입
              </Button>
            )}
            <Field
              label="지역명 (예: 서울특별시)"
              value={region}
              onChangeText={setRegion}
              maxLength={30}
            />
            <Field
              label="기관명 (선택)"
              value={name}
              onChangeText={setName}
              maxLength={100}
            />
            <Button
              disabled={busy || kind === "anonymous"}
              onPress={() => search(false)}
            >
              지역명으로 검색
            </Button>
            <ErrorNote message={error} />
            {busy && <T>의료기관을 찾고 있어요…</T>}
          </Card>
          {result && (
            <Card>
              <T>공공데이터 조회 상태: {result.status}</T>
              {!result.items?.length && (
                <T>
                  조회된 기관이 없어요. 검색 조건 또는 데이터 연결 상태를 확인해
                  주세요.
                </T>
              )}
              {result.items?.map((p) => (
                <Card key={p.id}>
                  <Title>{p.name}</Title>
                  <T>{p.address}</T>
                  <T>{p.tel || "전화번호 정보 없음"}</T>
                  <PolicyLink url={naverMapSearchUrl(p)}>
                    네이버 지도로 보기
                  </PolicyLink>
                </Card>
              ))}
              {result.sourceUrl && (
                <PolicyLink base={api.base} url={result.sourceUrl}>
                  데이터 출처
                </PolicyLink>
              )}
              <T>
                지도 버튼은 기관명·주소로 네이버 지도 검색 화면을 열어요. 같은
                이름의 기관은 주소도 함께 확인해 주세요.
              </T>
              <T>
                방문 전 전화로 진료 가능 여부를 확인해 주세요. 최대 20개 검색
                결과예요.
              </T>
            </Card>
          )}
        </>
      )}
      <EmergencyHelp />
    </>
  );
}
