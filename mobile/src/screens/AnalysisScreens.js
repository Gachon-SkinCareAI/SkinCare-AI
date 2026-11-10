import React, { useState } from "react";
import { View, Image, Platform } from "react-native";
import { Card, Title, T, Button, C } from "../components/Common";
import {
  Check,
  Field,
  PolicyLink,
  EmergencyHelp,
} from "../components/ConsentControls";
import { PrivateImage } from "../components/PrivateImage";
import { RiskBadge, RiskSummary } from "../components/RiskIndicator";
import { SCENARIOS, scenarioKey } from "../services/scenarios";
import ProgressForm from "../components/ProgressForm";
import { recordDate } from "../services/progress";
import { PhotoZoom } from "./ComparisonScreen";
import { RecordList, NewWound, RecordDetail } from "./RecordScreens";

export function UploadScreen({
  photo,
  kind,
  policy,
  choices,
  setChoices,
  busy,
  onSubmit,
  onChange,
  api,
  consent,
  onReload,
  capturedOn,
  setCapturedOn,
  followup,
}) {
  const firstGuest = kind === "anonymous";
  return (
    <>
      <NewWound
        photo={photo}
        date={capturedOn}
        setDate={setCapturedOn}
        target={followup}
        onChangePhoto={onChange}
        busy={busy}
      />
      <Card>
        <T>
          사진과 문진 답변은 분석 및 기록 조회를 위해 서버에 저장돼요.
          수집일부터 최대 5년이며 기록 삭제를 요청하면 조기 삭제돼요.
        </T>
        {firstGuest ? (
          <>
            <T>로그인 없이 시작할 수 있어요. 학습용 이용은 별도 선택이에요.</T>
            {!policy ? (
              <Button secondary disabled={busy} onPress={onReload}>
                동의 안내 다시 불러오기
              </Button>
            ) : (
              <>
                <PolicyLink base={api.base} url={policy.serviceUrl}>
                  비회원 서비스·개인정보 안내 읽기
                </PolicyLink>
                <Check
                  label="[필수] 비회원 서비스 이용 및 개인정보 처리 동의"
                  value={choices.service}
                  disabled={busy}
                  onChange={(service) => setChoices({ ...choices, service })}
                />
                <PolicyLink base={api.base} url={policy.healthUrl}>
                  건강정보 처리 내용 읽기
                </PolicyLink>
                <Check
                  label="[필수] 상처 사진·문진 등 건강정보 처리 동의"
                  value={choices.health}
                  disabled={busy}
                  onChange={(health) => setChoices({ ...choices, health })}
                />
                <PolicyLink base={api.base} url={policy.trainingUrl}>
                  AI 학습용 이용 안내 읽기
                </PolicyLink>
                <Check
                  label="[선택] 이후 제출할 자료의 AI 모델 개선용 이용 동의"
                  value={choices.training}
                  disabled={busy}
                  onChange={(training) => setChoices({ ...choices, training })}
                />
              </>
            )}
          </>
        ) : (
          <T>
            {consent?.trainingGranted
              ? "현재 학습 이용에 동의한 상태예요. 이 사진은 검수 대기로 분류되며 자동 학습되지는 않아요."
              : "이 사진은 학습용으로 이용하지 않아요. 동의 변경은 마이에서 할 수 있어요."}
          </T>
        )}
        {kind !== "member" && (
          <T style={{ color: C.muted }}>
            비회원은 이번 분석의 문진·결과만 확인할 수 있어요. 홈과 기록 탭에는
            과거 분석을 표시하지 않아요. 사진·문진은 위 보관 기준에 따라 서버에
            저장되며 마이에서 비회원 자료를 삭제할 수 있어요.
            {Platform.OS === "web"
              ? " 웹에서는 새로고침 후 접근키가 유지되지 않아요."
              : " 앱 삭제·기기 변경으로 접근키를 잃으면 자동 복구할 수 없어요."}
          </T>
        )}
        <Button
          disabled={
            busy ||
            (firstGuest && (!policy || !choices.service || !choices.health))
          }
          onPress={onSubmit}
        >
          {busy ? "사진을 분석하고 있어요…" : "사진 분석하고 문진 시작"}
        </Button>
      </Card>
      <EmergencyHelp />
    </>
  );
}

export function AnalysisScreen({
  session,
  api,
  token,
  busy,
  onAnswer,
  onRetry,
  onRefresh,
  onDelete,
  onFollowup,
  onProgress,
  onTimeline,
  memberHistory = false,
}) {
  const [text, setText] = useState(""),
    [remove, setRemove] = useState(false);
  const [zoom, setZoom] = useState(false);
  const scenario =
    SCENARIOS[
      scenarioKey({ slots: session.slots || {}, vision: session.vision || {} })
    ];
  const result = session.result;
  return (
    <>
      {result && (
        <RiskSummary code={result.level}>
          <T>{scenario.result}</T>
        </RiskSummary>
      )}
      <RecordDetail
        record={session}
        api={api}
        token={token}
        title={scenario.title}
        description={scenario.description}
        onZoom={() => setZoom(true)}
      />
      {session.status === "RETRY_REQUIRED" ? (
        <Card>
          <Title>분석을 이어가야 해요</Title>
          <T>
            사진은 저장됐지만 서버가 응답하지 못했어요. 연결을 확인하고 다시
            시도해 주세요.
          </T>
          <Button disabled={busy} onPress={onRetry}>
            분석 다시 시도
          </Button>
        </Card>
      ) : session.question && !result ? (
        <Card>
          <Title>문진 {session.turn + 1}</Title>
          <T style={{ fontSize: 19, lineHeight: 29 }}>
            {session.question.utterance}
          </T>
          {(session.question.choices || []).map((c) => (
            <Button
              key={c.value}
              secondary
              disabled={busy}
              onPress={() => onAnswer(c.value, text)}
            >
              {c.label}
            </Button>
          ))}
          <Field
            label="추가로 설명할 내용 (선택)"
            value={text}
            maxLength={1000}
            multiline
            onChangeText={setText}
            editable={!busy}
          />
          {(!session.question.choices?.length || text.trim()) && (
            <Button disabled={busy} onPress={() => onAnswer(null, text)}>
              {session.question.choices?.length
                ? "설명으로 답변하기"
                : "응답하고 계속"}
            </Button>
          )}
          {busy && (
            <T accessibilityLiveRegion="polite">응답을 확인하고 있어요…</T>
          )}
        </Card>
      ) : null}
      {result && (
        <Card>
          <Title>확인 정보와 출처</Title>
          {result.guide_available && result.guide?.cards?.length ? (
            result.guide.cards.map((card, i) => (
              <View key={card.id || i} style={{ gap: 8 }}>
                <Title>{card.title}</Title>
                <T>{card.body}</T>
                {card.source_url && (
                  <PolicyLink base={api.base} url={card.source_url}>
                    {card.source || "공식 출처 보기"}
                  </PolicyLink>
                )}
              </View>
            ))
          ) : (
            <T>
              현재 제공할 수 있는 검수된 케어 정보가 없어요. 결과만으로 처치를
              결정하지 마세요.
            </T>
          )}
        </Card>
      )}
      <EmergencyHelp />
      {!!session.answers?.length && (
        <Card>
          <Title>저장된 문진 답변</Title>
          {session.answers.map((a, i) => (
            <View key={i}>
              <T>{a.questionText || a.questionId}</T>
              <T style={{ color: C.muted }}>
                {a.answerLabel || a.answer || ""}
                {a.userText ? ` · ${a.userText}` : ""}
              </T>
            </View>
          ))}
        </Card>
      )}
      {memberHistory ? (
        <ProgressForm
          session={session}
          busy={busy}
          onSave={onProgress}
          onCompare={onTimeline}
          onFollowup={onFollowup}
        />
      ) : (
        <Card>
          <Title>이번 분석 결과</Title>
          <T>
            비회원은 현재 분석만 확인할 수 있어요. 앱을 다시 열거나 새 분석을
            시작하면 이전 결과를 화면에서 다시 열 수 없어요. 날짜별 경과 관리는
            소셜 로그인 후 이용해 주세요.
          </T>
        </Card>
      )}
      {zoom && (
        <PhotoZoom
          record={session}
          api={api}
          token={token}
          onClose={() => setZoom(false)}
        />
      )}
      <Button secondary disabled={busy} onPress={onRefresh}>
        저장된 상태 새로 불러오기
      </Button>
      <Card>
        <T>
          {session.trainingEligible
            ? "학습 이용 동의 · 검수 대기 자료"
            : "학습 이용 대상 제외"}
        </T>
        {remove ? (
          <>
            <T>이 사진·문진·결과를 삭제할까요? 삭제 후 복구할 수 없어요.</T>
            <Button disabled={busy} onPress={onDelete}>
              이 기록 삭제 확인
            </Button>
            <Button secondary disabled={busy} onPress={() => setRemove(false)}>
              취소
            </Button>
          </>
        ) : (
          <Button secondary disabled={busy} onPress={() => setRemove(true)}>
            이 기록 삭제
          </Button>
        )}
      </Card>
    </>
  );
}

export function HistoryScreen(props) {
  return <RecordList {...props} />;
}
