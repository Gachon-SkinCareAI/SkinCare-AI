import React, { useState } from "react";
import { View, Pressable, StyleSheet } from "react-native";
import Svg, { Path, Circle } from "react-native-svg";
import { Card, Title, T, Button, C } from "../components/Common";
import {
  Check,
  Field,
  PolicyLink,
  ErrorNote,
} from "../components/ConsentControls";

export function LoginScreen({ providers, busy, onLogin, onSkip }) {
  return (
    <Card>
      <Title>필요할 때 로그인하세요</Title>
      <T>
        사진 분석은 로그인 없이도 가능해요. 회원 기능은 소셜 계정으로 이용할 수
        있어요.
      </T>
      {providers.map((p) => (
        <Button
          key={p.id}
          disabled={busy || !p.enabled}
          onPress={() => onLogin(p.id)}
        >
          {p.label}로 계속하기{!p.enabled ? " · 준비 중" : ""}
        </Button>
      ))}
      {!providers.length && <T>로그인 제공자 정보를 불러오는 중이에요.</T>}
      <Button secondary disabled={busy} onPress={onSkip}>
        로그인 없이 홈으로
      </Button>
    </Card>
  );
}
export function SignupConsentScreen({ policy, api, busy, onAccept, onSkip }) {
  const [v, setV] = useState({
    terms: false,
    privacy: false,
    health: false,
    location: false,
    training: false,
  });
  const items = [
    ["terms", "이용약관", false],
    ["privacy", "개인정보 처리", false],
    ["health", "건강정보 처리", false],
    ["location", "주변 병원·약국 검색을 위한 위치정보 수집·이용", true],
    ["training", "AI 모델 개선용 사진·문진 이용", true],
  ].map(([key, label, optional]) => {
    const doc = policy?.[key === "health" ? "healthData" : key];
    return {
      key,
      label,
      optional,
      doc,
      available:
        !!doc?.url &&
        !!doc?.version &&
        (key !== "training" || !!policy?.trainingAvailable),
    };
  });
  const available = items.filter((item) => item.available),
    allSelected = !!available.length && available.every((item) => v[item.key]);
  return (
    <Card>
      <Title>처음 이용하기 전에 확인해 주세요</Title>
      <T>
        필수 항목에 동의하면 가입할 수 있어요. 위치정보와 학습 이용은 선택이며,
        각각 선택하거나 전체 동의를 이용할 수 있어요.
      </T>
      {policy ? (
        <>
          {items.map(({ key, label, optional, doc, available }) => (
            <React.Fragment key={key}>
              <PolicyLink base={api.base} url={doc?.url}>
                {label} 내용 읽기
              </PolicyLink>
              <Check
                disabled={busy || !available}
                label={`[${optional ? "선택" : "필수"}] ${label} 동의`}
                value={v[key]}
                onChange={(value) => setV((old) => ({ ...old, [key]: value }))}
              />
              {key === "location" && (
                <T>
                  주변 기관 검색에 현재 위도·경도를 사용해요. 위치 동의 후
                  가입을 완료하면 앱에서 기기 위치 권한을 요청해요. 좌표는
                  검색할 때만 조회하며 위치 이력을 저장하지 않아요. 권한을
                  거절해도 가입할 수 있어요.
                </T>
              )}
              {key === "training" && (
                <T>
                  {available
                    ? "동의한 이후 제출할 사진·문진만 모델 개선 검수 대상에 포함돼요. 동의하지 않아도 분석할 수 있어요."
                    : "학습 이용 안내가 준비되지 않아 지금은 선택할 수 없어요."}
                </T>
              )}
            </React.Fragment>
          ))}
          <Button
            secondary
            testID="signup-agree-all"
            disabled={busy || !policy.requiredAvailable || !available.length}
            onPress={() =>
              setV(
                Object.fromEntries(
                  items.map((item) => [
                    item.key,
                    item.available && !allSelected,
                  ]),
                ),
              )
            }
          >
            {allSelected ? "전체 동의 해제" : "전체 동의 (선택 항목 포함)"}
          </Button>
          <T>
            전체 동의를 누르면 위의 선택 항목도 함께 선택돼요. 가입 완료 전 개별
            항목을 다시 변경할 수 있어요.
          </T>
        </>
      ) : (
        <T>동의 문서를 불러오는 중이에요.</T>
      )}
      <Button
        disabled={
          busy ||
          !policy?.requiredAvailable ||
          !v.terms ||
          !v.privacy ||
          !v.health
        }
        onPress={() => onAccept(v)}
      >
        선택한 내용으로 가입 완료
      </Button>
      <Button secondary disabled={busy} onPress={onSkip}>
        나중에 가입하고 비회원으로 이용
      </Button>
    </Card>
  );
}
export function PrivacySettingsScreen({
  kind,
  consent,
  api,
  busy,
  onTraining,
  onLocation,
  onReload,
}) {
  return (
    <Card>
      <Title>개인정보 설정</Title>
      <T>가입·분석 때 선택한 동의를 확인하고 변경할 수 있어요.</T>
      {consent ? (
        <>
          <PolicyLink base={api.base} url={consent.trainingPolicyUrl}>
            AI 모델 개선용 이용 안내
          </PolicyLink>
          <Check
            label="이후 제출할 자료의 학습용 이용 동의"
            value={!!consent.trainingGranted}
            disabled={
              busy || (!consent.trainingAvailable && !consent.trainingGranted)
            }
            onChange={onTraining}
          />
          <T>
            미동의 상태의 자료는 제외돼요. 철회하면 기존 검수 대기 자료도
            대상에서 제외되며 다시 동의해도 자동 복구되지 않아요.
          </T>
          {kind === "member" && (
            <>
              <PolicyLink base={api.base} url={consent.locationPolicyUrl}>
                위치정보 이용 안내
              </PolicyLink>
              <Check
                label="주변 병원·약국 검색을 위한 위치정보 수집·이용 동의"
                value={!!consent.locationGranted}
                disabled={busy}
                onChange={onLocation}
              />
              <T>
                기기 위치 권한과 별도로 관리돼요. 동의를 철회하면 현재 위치
                검색을 사용할 수 없고, 지역명 검색은 계속 가능해요.
              </T>
            </>
          )}
        </>
      ) : (
        <Button secondary disabled={busy} onPress={onReload}>
          동의 상태 다시 불러오기
        </Button>
      )}
    </Card>
  );
}
export function MyScreen({
  kind,
  api,
  busy,
  onLogin,
  onLogout,
  onDelete,
  onReload,
  onInquiry,
  onPrivacy,
}) {
  const [confirm, setConfirm] = useState(""),
    [deleting, setDeleting] = useState(false);
  const [profile, setProfile] = useState(null),
    [profileError, setProfileError] = useState(""),
    [reload, setReload] = useState(0);
  const member = kind === "member",
    hasSession = member || kind === "guest";
  const provider = { GOOGLE: "구글", KAKAO: "카카오", NAVER: "네이버" }[
    profile?.provider
  ];
  const deleteWord = member ? "탈퇴" : "삭제";
  React.useEffect(() => {
    let live = true;
    setProfile(null);
    setProfileError("");
    if (kind === "member")
      api
        .request("/api/wc/users/me")
        .then((p) => {
          if (live) setProfile(p);
        })
        .catch((e) => {
          if (live) setProfileError(e.message);
        });
    return () => {
      live = false;
    };
  }, [api, kind, reload]);
  React.useEffect(() => {
    setDeleting(false);
    setConfirm("");
  }, [kind]);

  return (
    <>
      <Card style={my.profileCard}>
        <View style={my.profileRow}>
          <View style={my.avatar}>
            <AccountIcon name="person" size={30} />
          </View>
          <View style={my.profileText}>
            <T style={my.eyebrow}>
              {member ? "내 계정" : "비회원으로 이용 중"}
            </T>
            <T style={my.profileName}>
              {member
                ? profile?.nickname || "회원님"
                : "필요할 때 로그인하세요"}
            </T>
            {member && (
              <T style={my.caption}>
                {profile
                  ? profile.email || "이메일 미제공"
                  : profileError
                    ? "계정 정보를 확인할 수 없어요"
                    : "계정 정보를 불러오는 중…"}
              </T>
            )}
          </View>
        </View>
        {member ? (
          <>
            <View style={my.provider}>
              <T style={my.providerText}>
                {provider ? `${provider}로 로그인` : "소셜 로그인 계정"}
              </T>
            </View>
            <ErrorNote message={profileError} />
            {!!profileError && (
              <Button
                secondary
                disabled={busy}
                onPress={() => setReload((v) => v + 1)}
              >
                회원 정보 다시 불러오기
              </Button>
            )}
          </>
        ) : (
          <>
            <T style={my.caption}>
              사진 분석은 로그인 없이도 가능해요. 기록 보관과 상처 경과 관리는
              로그인 후 이용해 주세요.
            </T>
            <Button disabled={busy} onPress={onLogin}>
              소셜 로그인·회원가입
            </Button>
            {kind === "guest" && (
              <T style={my.smallNote}>
                비회원 분석은 현재 결과만 보여요. 기존 자료는 가입한 계정으로
                자동 이전되지 않아요.
              </T>
            )}
          </>
        )}
      </Card>

      <View style={my.section}>
        <T style={my.sectionTitle}>
          {hasSession ? "설정 및 도움말" : "이용 도움말"}
        </T>
        <Card style={my.menuCard}>
          {hasSession ? (
            <>
              <AccountMenuRow
                icon="shield"
                title="개인정보 설정"
                detail="위치정보·학습 이용 동의 관리"
                disabled={busy}
                onPress={onPrivacy}
                divider={member}
              />
              {member && (
                <AccountMenuRow
                  icon="chat"
                  title="문의 내역"
                  detail="문의 작성 · 초안 · 답변 확인"
                  disabled={busy}
                  onPress={onInquiry}
                />
              )}
            </>
          ) : (
            <AccountMenuRow
              icon="refresh"
              title="이 기기 연결 다시 확인"
              disabled={busy}
              onPress={onReload}
            />
          )}
        </Card>
      </View>

      {member && (
        <AccountMenuRow
          icon="logout"
          title="로그아웃"
          detail="비회원 홈으로 돌아가기"
          disabled={busy}
          onPress={onLogout}
        />
      )}
      {hasSession && (
        <>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={member ? "회원 탈퇴" : "비회원 자료 전체 삭제"}
            accessibilityState={{ disabled: !!busy, expanded: deleting }}
            disabled={busy}
            onPress={() => {
              setDeleting((value) => !value);
              setConfirm("");
            }}
            style={({ pressed }) => [
              my.deleteToggle,
              { opacity: busy ? 0.4 : pressed ? 0.6 : 1 },
            ]}
          >
            <T style={my.smallNote}>
              {member ? "회원 탈퇴" : "비회원 자료 전체 삭제"}{" "}
              {deleting ? "⌃" : "›"}
            </T>
          </Pressable>
          {deleting && (
            <Card style={my.deleteCard}>
              <Title>
                {member ? "회원 탈퇴" : "이 기기의 비회원 자료 삭제"}
              </Title>
              <T>
                사진·문진·결과와 관련 서비스 자료를 삭제해요. 관리 백업에도 삭제
                요청을 반영해요.
              </T>
              <Field
                label={`확인하려면 ${deleteWord} 입력`}
                value={confirm}
                onChangeText={setConfirm}
                editable={!busy}
              />
              <Button
                style={{ backgroundColor: "#A52D3D" }}
                disabled={busy || confirm !== deleteWord}
                onPress={() => onDelete(confirm)}
              >
                삭제 요청 확정
              </Button>
              <Button
                secondary
                disabled={busy}
                onPress={() => {
                  setDeleting(false);
                  setConfirm("");
                }}
              >
                취소
              </Button>
            </Card>
          )}
        </>
      )}
    </>
  );
}

function AccountIcon({ name, size = 22 }) {
  const paths = {
    shield: "M12 3 4 6v6c0 4 4 7 8 9 4-2 8-5 8-9V6z M8 12l3 3 5-6",
    chat: "M5 4h14a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H9l-6 3V6a2 2 0 0 1 2-2z M7 9h10 M7 13h6",
    refresh: "M20 8a8 8 0 1 0 0 8 M20 3v5h-5",
    logout: "M9 4H4v16h5 M10 12h11 M17 8l4 4-4 4",
  };
  return (
    <Svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke={C.navy}
      strokeWidth={1.6}
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      {name === "person" ? (
        <>
          <Circle cx="12" cy="8" r="4" />
          <Path d="M4 21v-2a8 8 0 0 1 16 0v2" />
        </>
      ) : (
        <Path d={paths[name]} />
      )}
    </Svg>
  );
}
function AccountMenuRow({ icon, title, detail, disabled, onPress, divider }) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={title}
      accessibilityState={{ disabled: !!disabled }}
      disabled={disabled}
      onPress={onPress}
      style={({ pressed }) => [
        my.menuRow,
        divider && my.divider,
        { opacity: disabled ? 0.4 : pressed ? 0.65 : 1 },
      ]}
    >
      <View style={my.menuIcon}>
        <AccountIcon name={icon} />
      </View>
      <View style={my.menuText}>
        <T style={my.menuTitle}>{title}</T>
        {detail && <T style={my.smallNote}>{detail}</T>}
      </View>
      <T style={my.chevron}>›</T>
    </Pressable>
  );
}
const my = StyleSheet.create({
  profileCard: { padding: 22, gap: 16 },
  profileRow: { flexDirection: "row", alignItems: "center", gap: 14 },
  avatar: {
    width: 58,
    height: 58,
    borderRadius: 21,
    backgroundColor: "#EDF2FA",
    alignItems: "center",
    justifyContent: "center",
  },
  profileText: { flex: 1, gap: 4 },
  eyebrow: { fontSize: 12, lineHeight: 18, color: C.muted },
  profileName: {
    fontSize: 20,
    lineHeight: 29,
    fontFamily: "NotoSansKR_700Bold",
  },
  caption: { color: C.muted, fontSize: 13, lineHeight: 21 },
  provider: {
    alignSelf: "flex-start",
    borderRadius: 8,
    paddingHorizontal: 10,
    paddingVertical: 4,
    backgroundColor: "#F3F6FA",
  },
  providerText: { color: C.navy, fontSize: 12, lineHeight: 19 },
  section: { gap: 9 },
  sectionTitle: {
    marginLeft: 4,
    fontSize: 12,
    color: C.muted,
    fontFamily: "NotoSansKR_700Bold",
  },
  menuCard: { paddingVertical: 0, paddingHorizontal: 16, gap: 0 },
  menuRow: {
    minHeight: 68,
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    paddingVertical: 15,
  },
  menuIcon: {
    width: 36,
    height: 36,
    borderRadius: 12,
    backgroundColor: "#F3F6FA",
    alignItems: "center",
    justifyContent: "center",
  },
  menuText: { flex: 1, gap: 3 },
  menuTitle: { fontSize: 15, fontFamily: "NotoSansKR_700Bold" },
  smallNote: { fontSize: 12, lineHeight: 20, color: C.muted },
  chevron: { fontSize: 24, lineHeight: 28, color: C.muted },
  divider: { borderBottomWidth: 1, borderBottomColor: C.line },
  deleteToggle: {
    minHeight: 44,
    alignItems: "center",
    justifyContent: "center",
  },
  deleteCard: { borderColor: "#EED5D9" },
});
