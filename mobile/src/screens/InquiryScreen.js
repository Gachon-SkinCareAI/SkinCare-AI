import React, { useEffect, useRef, useState } from "react";
import { Platform, View } from "react-native";
import { Card, Title, T, Button } from "../components/Common";
import { Field, ErrorNote } from "../components/ConsentControls";
import {
  readInquiryDraft,
  saveInquiryDraft,
  clearInquiryDraft,
} from "../stores/inquiryDraft";
import { requestId } from "../stores/session";
import { createClient } from "../services/client";
import { fetch as nativeFetch } from "expo/fetch";
const TYPES = [
  ["SYSTEM_ERROR", "앱 이용 오류"],
  ["AI_RESULT", "분석 결과 문의"],
  ["ACCURACY", "분석 정확도"],
  ["OTHER", "기타"],
];
const empty = () => ({
  title: "",
  content: "",
  type: "OTHER",
  clientRequestId: requestId(),
});
export default function InquiryScreen({
  api: sharedApi,
  token,
  detailId,
  onOpen,
}) {
  const api = React.useMemo(
    () => createClient(sharedApi.base, () => token, nativeFetch),
    [sharedApi.base, token],
  );
  const [owner, setOwner] = useState(null),
    [draft, setDraft] = useState(empty),
    [rows, setRows] = useState([]),
    [detail, setDetail] = useState(null);
  const [page, setPage] = useState(0),
    [hasMore, setHasMore] = useState(false),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [notice, setNotice] = useState("");
  const alive = useRef(true),
    lock = useRef(false),
    [ready, setReady] = useState(false);
  async function list(next = 0) {
    const r = await api.request(`/api/wc/inquiries?page=${next}&size=20`);
    if (!alive.current) return;
    setRows((old) => (next ? [...old, ...r.items] : r.items));
    setPage(next);
    setHasMore(r.hasNext);
  }
  async function act(fn) {
    if (lock.current) return;
    lock.current = true;
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await fn();
    } catch (e) {
      if (alive.current) setError(e.message);
    } finally {
      lock.current = false;
      if (alive.current) setBusy(false);
    }
  }
  async function load() {
    const profile = await api.request("/api/wc/users/me");
    if (!alive.current) return;
    setOwner(profile.userId);
    if (detailId) {
      const value = await api.request(
        `/api/wc/inquiries/${encodeURIComponent(detailId)}`,
      );
      if (alive.current) setDetail(value);
    } else {
      const saved = await readInquiryDraft(profile.userId);
      if (alive.current) {
        if (saved) setDraft(saved);
        setReady(true);
      }
      await list();
    }
  }
  useEffect(() => {
    alive.current = true;
    act(load);
    return () => {
      alive.current = false;
    };
  }, [detailId]);
  function change(name, value) {
    setDraft((old) => ({
      ...old,
      [name]: value,
      clientRequestId: requestId(),
    }));
  }
  async function submit() {
    if (!draft.title.trim() || !draft.content.trim())
      throw new Error("제목과 문의 내용을 입력해 주세요.");
    await saveInquiryDraft(owner, draft); // Retry the same request ID after a response loss.
    if (!alive.current) return;
    const value = await api.post("/api/wc/inquiries", draft);
    await clearInquiryDraft(owner);
    if (!alive.current) return;
    setDraft(empty());
    setNotice("문의를 접수했어요.");
    await list();
    if (alive.current) onOpen(value.id);
  }
  return (
    <>
      <ErrorNote message={error} />
      {notice && <T accessibilityLiveRegion="polite">{notice}</T>}
      {error && (
        <Button secondary disabled={busy} onPress={() => act(load)}>
          문의 다시 불러오기
        </Button>
      )}
      {detailId ? (
        detail && (
          <Card>
            <Title>{detail.title}</Title>
            <T>
              {detail.status === "COMPLETED" ? "답변 완료" : "답변 대기"} ·{" "}
              {String(detail.createdAt).slice(0, 10)}
            </T>
            <T>{detail.content}</T>
            <Title>관리자 답변</Title>
            <T>{detail.answer?.content || "아직 답변이 등록되지 않았어요."}</T>
            <Button secondary disabled={busy} onPress={() => act(load)}>
              답변 새로고침
            </Button>
          </Card>
        )
      ) : (
        <>
          <Card>
            <Title>문의 작성</Title>
            <T>서비스 이용 문의를 남겨 주세요. 응급 상담 창구는 아니에요.</T>
            <T>
              {Platform.OS === "web"
                ? "웹 초안은 이 화면 세션에서만 보관되며 새로고침 시 사라져요."
                : "초안 저장을 누르면 이 기기에 암호화해 보관해요. 접수 전에는 서버로 전송되지 않아요."}
            </T>
            <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 6 }}>
              {TYPES.map(([id, label]) => (
                <Button
                  key={id}
                  secondary={draft.type !== id}
                  disabled={busy || !ready}
                  onPress={() => change("type", id)}
                >
                  {label}
                </Button>
              ))}
            </View>
            <Field
              label="문의 제목"
              value={draft.title}
              onChangeText={(v) => change("title", v)}
              maxLength={200}
              editable={!busy && ready}
            />
            <Field
              label="문의 내용"
              value={draft.content}
              onChangeText={(v) => change("content", v)}
              multiline
              maxLength={5000}
              editable={!busy && ready}
            />
            <Button
              secondary
              disabled={busy || !ready}
              onPress={() =>
                act(async () => {
                  await saveInquiryDraft(owner, draft);
                  setNotice("문의 초안을 이 기기에 저장했어요.");
                })
              }
            >
              문의 초안 저장
            </Button>
            <Button
              secondary
              disabled={busy || !owner}
              onPress={() =>
                act(async () => {
                  await clearInquiryDraft(owner);
                  setDraft(empty());
                  setReady(true);
                  setNotice("문의 초안을 삭제했어요.");
                })
              }
            >
              문의 초안 삭제
            </Button>
            <Button
              disabled={
                busy || !ready || !draft.title.trim() || !draft.content.trim()
              }
              onPress={() => act(submit)}
            >
              문의 접수하기
            </Button>
          </Card>
          <Card>
            <Title>내 문의</Title>
            {!rows.length && <T>접수한 문의가 없어요.</T>}
            {rows.map((r) => (
              <Button
                secondary
                key={r.id}
                disabled={busy}
                onPress={() => onOpen(r.id)}
              >
                {r.title} ·{" "}
                {r.status === "COMPLETED" ? "답변 완료" : "답변 대기"}
              </Button>
            ))}
            {hasMore && (
              <Button
                secondary
                disabled={busy}
                onPress={() => act(() => list(page + 1))}
              >
                이전 문의 더 보기
              </Button>
            )}
          </Card>
        </>
      )}
    </>
  );
}
