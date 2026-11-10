import React, { useEffect, useRef, useState } from "react";
import { BackHandler, StatusBar, Platform } from "react-native";
import * as Location from "expo-location";
import { fetch as nativeFetch } from "expo/fetch";
import { Layout, Card, T, Title, Button } from "./components/Common";
import { ErrorNote } from "./components/ConsentControls";
import HomeScreen from "./screens/HomeScreen";
import WelcomeScreen from "./screens/WelcomeScreen";
import CaptureScreen from "./screens/CaptureScreen";
import {
  UploadScreen,
  AnalysisScreen,
  HistoryScreen,
} from "./screens/AnalysisScreens";
import {
  LoginScreen,
  SignupConsentScreen,
  MyScreen,
  PrivacySettingsScreen,
} from "./screens/AccountScreens";
import { completeSignup } from "./services/signup";
import MedicalScreen from "./screens/MedicalScreen";
import { createClient } from "./services/client";
import { normalizePhoto, uploadForm } from "./services/photos";
import { startSocialLogin, finishWebLogin } from "./services/social";
import {
  readSessionKey,
  saveSessionKey,
  guestKey,
  requestId,
} from "./stores/session";
import { clearInquiryDraft } from "./stores/inquiryDraft";
import { today, validateDate } from "./utils/draft";
import { recordDate } from "./services/progress";
import ComparisonScreen from "./screens/ComparisonScreen";
import EncyclopediaScreen from "./screens/EncyclopediaScreen";
import InquiryScreen from "./screens/InquiryScreen";

const ANONYMOUS = { kind: "anonymous", token: null };
const API_URL =
  process.env.EXPO_PUBLIC_API_URL ||
  "https://looksmart-longer-flashing-forestry.trycloudflare.com";
export default function AppShell() {
  const [identity, setIdentity] = useState(ANONYMOUS),
    identityRef = useRef(ANONYMOUS);
  const apiRef = useRef(null);
  if (!apiRef.current)
    apiRef.current = createClient(
      API_URL,
      () => identityRef.current.token,
      nativeFetch,
    );
  const api = apiRef.current;
  const [stack, setStack] = useState([{ name: "welcome" }]);
  const route = stack.at(-1);
  const [records, setRecords] = useState([]),
    [page, setPage] = useState(0),
    [more, setMore] = useState(false);
  const [current, setCurrent] = useState(null),
    [consent, setConsent] = useState(null);
  const [policy, setPolicy] = useState(null),
    [signupPolicy, setSignupPolicy] = useState(null),
    [providers, setProviders] = useState([]);
  const [photo, setPhoto] = useState(null),
    [parent, setParent] = useState(null);
  const [capturedOn, setCapturedOn] = useState(today);
  const [choices, setChoices] = useState({
    service: false,
    health: false,
    training: false,
  });
  const [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [notice, setNotice] = useState("");
  const [leaving, setLeaving] = useState(null),
    lock = useRef(false),
    mounted = useRef(true);
  const upload = useRef(null),
    answerAttempt = useRef(null),
    restorePromise = useRef(null);

  function identify(value) {
    identityRef.current = value;
    setIdentity(value);
    setConsent(null);
    setCurrent(null);
    setRecords([]);
    setMore(false);
    if (value.kind === "anonymous")
      setChoices({ service: false, health: false, training: false });
  }
  function rememberRecord(value) {
    setCurrent(value);
    setRecords((old) => old.map((row) => (row.id === value.id ? value : row)));
  }
  function push(name, params = {}) {
    setStack((s) => [...s, { name, ...params }]);
  }
  function home() {
    setStack([{ name: "home" }]);
  }
  async function run(work) {
    if (lock.current) return;
    lock.current = true;
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await work();
    } catch (e) {
      if (mounted.current)
        setError(e.message || "요청을 완료하지 못했어요. 다시 시도해 주세요.");
    } finally {
      lock.current = false;
      if (mounted.current) setBusy(false);
    }
  }
  async function consentState() {
    const id = identityRef.current;
    if (!id.token) return;
    const value = await api.request(
      id.kind === "guest"
        ? "/api/wc/guest/consents"
        : "/api/wc/users/me/consents",
    );
    if (mounted.current && identityRef.current.token === id.token)
      setConsent(value);
    return value;
  }
  async function list(next = 0, caseId) {
    if (
      !identityRef.current.token ||
      identityRef.current.kind !== "member" ||
      identityRef.current.onboarding
    ) {
      setRecords([]);
      setMore(false);
      return;
    }
    const id = identityRef.current.token;
    const values = await api.list(next, caseId);
    if (mounted.current && id === identityRef.current.token) {
      setRecords((old) =>
        next === 0
          ? values
          : [...old, ...values.filter((v) => !old.some((x) => x.id === v.id))],
      );
      setPage(next);
      setMore(values.length === 20);
    }
  }
  async function acceptLogin(value) {
    await saveSessionKey("member", value.token);
    identify({
      kind: "member",
      token: value.token,
      onboarding: !!value.consentRequired,
    });
    if (value.consentRequired) {
      setStack([{ name: "home" }, { name: "signup" }]);
      setSignupPolicy(await api.request("/api/wc/auth/consent-policy"));
    } else {
      home();
      await consentState();
      await list();
    }
  }
  async function resumeGuest() {
    const key = await readSessionKey("guest");
    if (!key) {
      identify(ANONYMOUS);
      return;
    }
    try {
      const value = await api.post("/api/wc/auth/guest/resume", {
        deviceKey: key,
      });
      identify({ kind: "guest", token: value.token });
      home();
      await consentState();
    } catch (e) {
      if (e.status === 401) {
        await saveSessionKey("guest", null);
        identify(ANONYMOUS);
      } else throw e;
    }
  }
  async function restore() {
    const callback = await finishWebLogin(api);
    if (callback) return acceptLogin(callback);
    const token = await readSessionKey("member");
    if (token) {
      identify({ kind: "member", token });
      try {
        await api.request("/api/wc/users/me");
        home();
        await consentState();
        await list();
        return;
      } catch (e) {
        if (e.status === 403) {
          identify({ kind: "member", token, onboarding: true });
          setSignupPolicy(await api.request("/api/wc/auth/consent-policy"));
          return;
        }
        if (e.status !== 401) throw e;
        await saveSessionKey("member", null);
      }
    }
    return resumeGuest();
  }
  useEffect(() => {
    mounted.current = true;
    restorePromise.current = restore().catch((e) => {
      if (mounted.current) setError(e.message);
    });
    api
      .request("/api/wc/auth/consent-policy")
      .then((value) => {
        if (mounted.current) setSignupPolicy(value);
      })
      .catch(() => {});
    api
      .request("/api/wc/auth/social/providers")
      .then((value) => {
        if (mounted.current) setProviders(value);
      })
      .catch((e) => {
        if (mounted.current) setError(e.message);
      });
    return () => {
      mounted.current = false;
    };
  }, []);
  function guarded(action) {
    if (lock.current) return;
    if (photo && route.name === "upload") {
      setLeaving(() => action);
      return;
    }
    setError("");
    setNotice("");
    action();
  }
  function back() {
    guarded(() =>
      setStack((s) => (s.length > 1 ? s.slice(0, -1) : [{ name: "home" }])),
    );
  }
  useEffect(() => {
    const sub = BackHandler.addEventListener("hardwareBackPress", () => {
      if (["home", "welcome"].includes(route.name) && !busy) return false;
      back();
      return true;
    });
    return () => sub.remove();
  }, [route.name, busy, photo]);
  function start(parentId = null) {
    if (lock.current) return;
    if (identityRef.current.kind !== "member") {
      parentId = null;
      setCurrent(null);
    }
    setError("");
    setNotice("");
    setPhoto(null);
    upload.current = null;
    setParent(parentId);
    setCapturedOn(today());
    if (identityRef.current.kind === "member") push("capture");
    else setStack([{ name: "home" }, { name: "capture" }]);
  }
  async function openRecord(id) {
    if (identityRef.current.kind !== "member")
      throw new Error("비회원은 이번 분석 결과만 확인할 수 있어요.");
    const value = await api.get(id);
    answerAttempt.current = null;
    rememberRecord(value);
    setStack([{ name: "history" }, { name: "detail", id }]);
  }
  function navigate(name, params = {}) {
    guarded(() => {
      if (name === "analysis") return start();
      if (name === "analysisDetail" || name === "recovery")
        return run(() => openRecord(params.id));
      setStack([{ name }]);
      if (name === "history")
        run(async () => {
          await restorePromise.current;
          await list();
        });
      if (name === "user") run(consentState);
    });
  }
  async function showLogin() {
    push("login");
    setProviders(await api.request("/api/wc/auth/social/providers"));
  }
  async function prepareUpload() {
    await restorePromise.current;
    if (identityRef.current.onboarding) {
      await saveSessionKey("member", null);
      await resumeGuest();
    }
    setPolicy(await api.request("/api/wc/auth/guest/policy"));
    if (identityRef.current.token) await consentState();
  }
  async function submit() {
    if (!photo) throw new Error("사진을 먼저 선택해 주세요.");
    const dateError = validateDate(capturedOn);
    if (dateError) throw new Error(dateError);
    if (!identityRef.current.token) {
      if (!policy || !choices.service || !choices.health)
        throw new Error("필수 동의 내용을 확인해 주세요.");
      const value = await api.post("/api/wc/auth/guest", {
        deviceKey: await guestKey(),
        agreedService: choices.service,
        agreedHealthData: choices.health,
        policyVersion: policy.version,
        trainingGranted: choices.training,
        trainingVersion: policy.trainingVersion,
      });
      identify({ kind: "guest", token: value.token });
      await consentState();
    }
    if (!upload.current)
      upload.current = {
        id: requestId(),
        photo: await normalizePhoto(photo),
        capturedOn,
      };
    const value = await api.create(
      await uploadForm(
        upload.current.photo,
        upload.current.id,
        parent || undefined,
        upload.current.capturedOn,
      ),
    );
    setCurrent(value);
    setPhoto(null);
    upload.current = null;
    answerAttempt.current = null;
    setStack([{ name: "history" }, { name: "detail", id: value.id }]);
    await list();
  }
  async function sendAnswer(answer, userText) {
    const payload = {
      expectedVersion: current.version,
      questionId: current.question.question_id,
      answer,
      userText,
    };
    const fingerprint = JSON.stringify(payload);
    if (
      !answerAttempt.current ||
      answerAttempt.current.fingerprint !== fingerprint
    )
      answerAttempt.current = { fingerprint, id: requestId() };
    const value = await api.answer(current.id, {
      ...payload,
      clientRequestId: answerAttempt.current.id,
    });
    rememberRecord(value);
    answerAttempt.current = null;
  }
  async function leaveMember() {
    if (identityRef.current.kind === "member")
      await api.post("/api/wc/auth/logout", {});
    await saveSessionKey("member", null);
    identify(ANONYMOUS);
    home();
    await resumeGuest();
  }
  const authReady = !identity.onboarding;
  if (route.name === "welcome")
    return (
      <>
        <StatusBar barStyle="dark-content" />
        <WelcomeScreen
          api={api}
          policy={signupPolicy}
          providers={providers}
          busy={busy}
          error={error}
          onGuest={home}
          onReload={() =>
            run(async () =>
              setProviders(await api.request("/api/wc/auth/social/providers")),
            )
          }
          onLogin={(provider) =>
            run(async () => {
              await restorePromise.current;
              const value = await startSocialLogin(api, provider);
              if (value) await acceptLogin(value);
            })
          }
        />
      </>
    );
  if (route.name === "capture")
    return (
      <CaptureScreen
        initialMode={route.mode}
        photo={photo}
        onSelect={(p) => {
          setPhoto(p);
          upload.current = null;
        }}
        onBack={back}
        onContinue={() => {
          push("upload");
          run(prepareUpload);
        }}
      />
    );
  const memberHistory = identity.kind === "member" && authReady;
  if (route.name === "home")
    return (
      <>
        <StatusBar barStyle="dark-content" />
        <HomeScreen
          onNavigate={navigate}
          api={api}
          token={identity.token}
          error={error}
          notice={notice}
          busy={busy}
          showHistory={memberHistory}
          analyses={
            memberHistory
              ? records.map((r) => ({
                  ...r,
                  title:
                    r.status === "COMPLETED" ? "분석 기록" : "문진 이어하기",
                  summary: recordDate(r),
                }))
              : []
          }
          recovery={null}
        />
      </>
    );
  const titles = {
    upload: "사진 분석 준비",
    detail: "사진·문진·결과",
    history: route.caseId ? "같은 상처의 경과" : "내 기록",
    login: "소셜 로그인",
    signup: "첫 가입 동의",
    user: "마이",
    privacy: "개인정보 설정",
    pin: "병원·약국",
    book: "피부·상처 백과",
    article: "백과 상세",
    comparison: "날짜별 사진 비교",
    inquiries: "내 문의",
    inquiry: "문의·답변",
    notifications: "알림",
  };
  const activeTab = ["article", "book"].includes(route.name)
    ? "book"
    : ["inquiries", "inquiry", "user", "privacy", "signup", "login"].includes(
          route.name,
        )
      ? "user"
      : route.name === "pin"
        ? "pin"
        : "history";
  return (
    <>
      <StatusBar barStyle="dark-content" />
      <Layout
        title={titles[route.name] || "SkinCare AI"}
        onBack={back}
        active={activeTab}
        onNavigate={navigate}
      >
        <ErrorNote message={error} />
        {!!notice && <T accessibilityLiveRegion="polite">{notice}</T>}
        {leaving && (
          <Card>
            <T>
              아직 올리지 않은 사진이 있어요. 화면을 나가면 이 사진 선택이
              해제돼요.
            </T>
            <Button
              onPress={() => {
                const action = leaving;
                setLeaving(null);
                setPhoto(null);
                upload.current = null;
                action();
              }}
            >
              사진 선택을 취소하고 나가기
            </Button>
            <Button secondary onPress={() => setLeaving(null)}>
              계속 작성
            </Button>
          </Card>
        )}
        {route.name === "upload" && photo && (
          <UploadScreen
            photo={photo}
            followup={!!parent}
            capturedOn={capturedOn}
            setCapturedOn={(value) => {
              setCapturedOn(value);
              upload.current = null;
            }}
            kind={identity.kind}
            api={api}
            consent={consent}
            policy={policy}
            choices={choices}
            setChoices={setChoices}
            busy={busy}
            onReload={() => run(prepareUpload)}
            onChange={() => push("capture", { mode: "gallery" })}
            onSubmit={() => run(submit)}
          />
        )}
        {route.name === "history" &&
          (memberHistory ? (
            <HistoryScreen
              records={records}
              api={api}
              token={identity.token}
              busy={busy}
              more={more}
              onAdd={() => start()}
              onOpen={(id) => run(() => openRecord(id))}
              onMore={() => run(() => list(page + 1, route.caseId))}
            />
          ) : (
            <Card>
              <Title>비회원은 이번 분석만 확인해요</Title>
              <T>
                과거 분석 목록과 날짜별 경과 관리는 회원 기능이에요. 기존 비회원
                자료는 계정에 자동으로 옮겨지지 않아요.
              </T>
              {current && (
                <Button
                  disabled={busy}
                  onPress={() => push("detail", { id: current.id })}
                >
                  이번 분석 이어서 보기
                </Button>
              )}
              <Button disabled={busy} onPress={() => start()}>
                새 상처 사진 분석
              </Button>
              <Button secondary disabled={busy} onPress={() => run(showLogin)}>
                소셜 로그인·회원가입
              </Button>
              {identity.kind === "guest" && (
                <Button
                  secondary
                  disabled={busy}
                  onPress={() => navigate("user")}
                >
                  비회원 자료 삭제·동의 관리
                </Button>
              )}
            </Card>
          ))}
        {route.name === "detail" && current && (
          <AnalysisScreen
            key={`${current.id}:${current.question?.question_id || current.status}`}
            session={current}
            api={api}
            token={identity.token}
            busy={busy}
            memberHistory={memberHistory}
            onAnswer={(a, t) => run(() => sendAnswer(a, t))}
            onRetry={() =>
              run(async () => rememberRecord(await api.retry(current)))
            }
            onRefresh={() =>
              run(async () => {
                rememberRecord(await api.get(current.id));
                answerAttempt.current = null;
              })
            }
            onDelete={() =>
              run(async () => {
                await api.remove(current.id);
                setCurrent(null);
                setStack([{ name: "history" }]);
                await list();
              })
            }
            onFollowup={() => start(current.id)}
            onTimeline={() =>
              push("comparison", { caseId: current.caseId, id: current.id })
            }
            onProgress={({ pain, note, capturedOn: date, swelling, exudate }) =>
              run(async () => {
                if (
                  pain !== "" &&
                  (!/^\d{1,2}$/.test(pain) || Number(pain) > 10)
                )
                  throw new Error("통증 점수는 0~10 정수로 입력해 주세요.");
                const dateError = date ? validateDate(date) : "";
                if (dateError) throw new Error(dateError);
                rememberRecord(
                  await api.progress(current.id, current.version, {
                    painScore: pain === "" ? null : Number(pain),
                    note,
                    capturedOn: date,
                    swelling,
                    exudate,
                  }),
                );
                setNotice("경과 기록을 저장했어요.");
              })
            }
          />
        )}
        {route.name === "comparison" && memberHistory && (
          <ComparisonScreen
            api={api}
            token={identity.token}
            caseId={route.caseId}
            initialId={route.id}
          />
        )}
        {route.name === "login" && (
          <LoginScreen
            providers={providers}
            busy={busy}
            onLogin={(p) =>
              run(async () => {
                const value = await startSocialLogin(api, p);
                if (value) await acceptLogin(value);
              })
            }
            onSkip={home}
          />
        )}
        {route.name === "signup" && (
          <SignupConsentScreen
            policy={signupPolicy}
            api={api}
            busy={busy}
            onSkip={() => run(leaveMember)}
            onAccept={(v) =>
              run(async () => {
                const message = await completeSignup({
                  api,
                  policy: signupPolicy,
                  choices: v,
                  requestLocationPermission: () =>
                    Platform.OS === "web"
                      ? Promise.resolve({ web: true })
                      : Location.requestForegroundPermissionsAsync(),
                });
                identify({ ...identityRef.current, onboarding: false });
                home();
                await consentState();
                await list();
                setNotice(message);
              })
            }
          />
        )}
        {route.name === "user" &&
          (!authReady ? (
            <Card>
              <Title>회원가입 동의가 남아 있어요</Title>
              <Button onPress={() => push("signup")}>동의 내용 확인</Button>
              <Button secondary onPress={() => run(leaveMember)}>
                비회원으로 계속
              </Button>
            </Card>
          ) : (
            <MyScreen
              key={identity.kind}
              kind={identity.kind}
              api={api}
              busy={busy}
              onLogin={() => run(showLogin)}
              onLogout={() => run(leaveMember)}
              onInquiry={() => push("inquiries")}
              onPrivacy={() => {
                push("privacy");
                run(consentState);
              }}
              onReload={() =>
                run(async () => {
                  await restore();
                  await consentState();
                })
              }
              onDelete={(confirmation) =>
                run(async () => {
                  const kind = identityRef.current.kind;
                  const profile =
                    kind === "member"
                      ? await api.request("/api/wc/users/me")
                      : null;
                  if (profile) await clearInquiryDraft(profile.userId);
                  await api.post(
                    kind === "guest" ? "/api/wc/guest" : "/api/wc/users/me",
                    { confirmation },
                    "DELETE",
                  );
                  await saveSessionKey(
                    kind === "guest" ? "guest" : "member",
                    null,
                  );
                  identify(ANONYMOUS);
                  home();
                })
              }
            />
          ))}
        {route.name === "privacy" && !!identity.token && authReady && (
          <PrivacySettingsScreen
            kind={identity.kind}
            consent={consent}
            api={api}
            busy={busy}
            onReload={() => run(consentState)}
            onTraining={(granted) =>
              run(async () => {
                await api.post(
                  identity.kind === "guest"
                    ? "/api/wc/guest/consents/training"
                    : "/api/wc/users/me/consents/training",
                  { granted, version: consent.trainingVersion },
                  "PUT",
                );
                await consentState();
              })
            }
            onLocation={(granted) =>
              run(async () => {
                await api.post(
                  "/api/wc/users/me/consents/location",
                  { granted, version: consent.locationVersion },
                  "PUT",
                );
                await consentState();
              })
            }
          />
        )}
        {route.name === "pin" && (
          <MedicalScreen
            api={api}
            kind={authReady ? identity.kind : "anonymous"}
            consent={consent}
            onLogin={() => run(showLogin)}
            onConsent={() => {
              push("privacy");
              run(consentState);
            }}
          />
        )}
        {["book", "article"].includes(route.name) && (
          <EncyclopediaScreen
            articleId={route.articleId}
            onOpen={(articleId) => push("article", { articleId })}
            onBack={back}
          />
        )}
        {["inquiries", "inquiry"].includes(route.name) &&
          identity.kind === "member" && (
            <InquiryScreen
              key={route.id || "list"}
              api={api}
              token={identity.token}
              detailId={route.id}
              onOpen={(id) => push("inquiry", { id })}
            />
          )}
        {route.name === "notifications" && (
          <Card>
            <T>새로운 알림이 없어요.</T>
          </Card>
        )}
        {!!error && (
          <Button
            secondary
            disabled={busy}
            onPress={() =>
              run(async () => {
                await restore();
                if (route.name === "login") await showLogin();
                if (route.name === "signup")
                  setSignupPolicy(
                    await api.request("/api/wc/auth/consent-policy"),
                  );
              })
            }
          >
            연결 상태 다시 확인
          </Button>
        )}
      </Layout>
    </>
  );
}
