import { useEffect, useRef, useState, type FormEvent } from "react";
import {
  AlertCircle,
  Check,
  ExternalLink,
  FileText,
  ShieldCheck,
} from "lucide-react";
import { Badge, Modal, Progress, RiskBadge } from "./components";
import {
  answerInquiry,
  getDashboard,
  getInquiry,
  syncHospitals,
  updateDiagnosisStatus,
  updateUserStatus,
  type HospitalSyncResult,
} from "./services/adminApi";
import { type Detail, type Inquiry, type OpenDetail, type Store } from "./data";

type Props = {
  detail: Detail;
  store: Store;
  update: (store: Store) => void;
  close: () => void;
  open: OpenDetail;
  notify: (s: string) => void;
  // 목록이 바뀌는 작업(동기화)이 끝났음을 의료기관 화면에 알린다
  onFacilitiesChanged?: () => void;
};
export default function Details({
  detail,
  store,
  update,
  close,
  open,
  notify,
  onFacilitiesChanged = () => {},
}: Props) {
  if (detail.type === "sync")
    return (
      <SyncDetails
        store={store}
        update={update}
        close={close}
        notify={notify}
        onFacilitiesChanged={onFacilitiesChanged}
      />
    );
  if (detail.type === "diagnosis") {
    const d = store.diagnoses.find((row) => row.id === detail.id);
    if (!d) return null;
    // 사용자 목록은 최근 일부만 불러오므로 목록에 없는 사용자일 수 있다
    const user = store.users.find((u) => u.id === d.userId);
    return (
      <Modal title="진단 상세" close={close}>
        <div className="detail-title">
          <span className="detail-icon">📋</span>
          <div>
            <h3>{d.id}</h3>
            <p>{d.time.replaceAll("-", ".")}</p>
          </div>
          <RiskBadge risk={d.risk} />
        </div>
        <dl className="details-grid">
          <div>
            <dt>사용자</dt>
            <dd>
              {user ? (
                <button
                  className="table-link"
                  onClick={() => open({ type: "user", id: user.id })}
                >
                  {user.name} · {user.id}
                </button>
              ) : (
                d.userId
              )}
            </dd>
          </div>
          <div>
            <dt>의심 유형</dt>
            <dd>{d.condition}</dd>
          </div>
          <div>
            <dt>분석 부위</dt>
            <dd>{d.area}</dd>
          </div>
          <div>
            <dt>현재 상태</dt>
            <dd>
              <Badge tone={d.status === "진행중" ? "blue" : "green"}>
                {d.status}
              </Badge>
            </dd>
          </div>
        </dl>
        <div className="detail-confidence">
          <span>모델 신뢰도</span>
          <strong>{d.confidence}%</strong>
          <Progress value={d.confidence} />
        </div>
        <label className="form-label">
          처리 상태
          <select
            aria-label="처리 상태"
            value={d.status}
            onChange={async (e) => {
              const status = e.target.value;
              try {
                await updateDiagnosisStatus(d.id, status);
                update({
                  ...store,
                  diagnoses: store.diagnoses.map((row) =>
                    row.id === d.id ? { ...row, status } : row,
                  ),
                });
                notify("진단 처리 상태를 저장했습니다");
              } catch (cause) {
                notify(
                  cause instanceof Error
                    ? cause.message
                    : "상태 저장에 실패했습니다",
                );
              }
            }}
          >
            <option>진행중</option>
            <option>알림발송</option>
            <option>완료</option>
            <option>처리완료</option>
          </select>
        </label>
        <div className="info-note">
          <ShieldCheck size={17} />
          <p>처리 상태는 관리자 API를 통해 저장됩니다</p>
        </div>
        <div className="modal-actions">
          <button className="button secondary" onClick={close}>
            닫기
          </button>
        </div>
      </Modal>
    );
  }
  if (detail.type === "user") {
    const u = store.users.find((row) => row.id === detail.id);
    if (!u) return null;
    const related = store.diagnoses.filter((d) => d.userId === u.id);
    return (
      <Modal title="사용자 상세" close={close}>
        <div className="detail-title">
          <span className="avatar large">{u.name.slice(0, 1)}</span>
          <div>
            <h3>{u.name}</h3>
            <p>{u.id}</p>
          </div>
          <Badge tone={u.active ? "green" : "gray"}>
            {u.active ? "활성" : "비활성"}
          </Badge>
        </div>
        <dl className="details-grid">
          <div>
            <dt>가입일</dt>
            <dd>{u.joinedAt.replaceAll("-", ".")}</dd>
          </div>
          <div>
            <dt>누적 진단</dt>
            <dd>{u.diagnoses}건</dd>
          </div>
          <div>
            <dt>최근 위험도</dt>
            <dd>
              <RiskBadge risk={u.risk} />
            </dd>
          </div>
          <div>
            <dt>플랜</dt>
            <dd>
              <Badge tone={u.plan === "Premium" ? "orange" : "gray"}>
                {u.plan}
              </Badge>
            </dd>
          </div>
        </dl>
        <h4 className="section-label">관련 진단</h4>
        {related.length ? (
          <div className="related-list">
            {related.map((d) => (
              <button
                key={d.id}
                onClick={() => open({ type: "diagnosis", id: d.id })}
              >
                <FileText size={15} />
                <span>
                  {d.id}
                  <small>
                    {d.condition} · {d.area}
                  </small>
                </span>
                <ExternalLink size={14} />
              </button>
            ))}
          </div>
        ) : (
          <p className="muted detail-empty">연결된 진단이 없습니다.</p>
        )}
        <p className="detail-footnote">
          상태 변경은 이 브라우저의 데모에만 반영됩니다.
        </p>
        <div className="modal-actions">
          <button
            className="button secondary"
            onClick={async () => {
              try {
                await updateUserStatus(u.id, !u.active);
                update({
                  ...store,
                  users: store.users.map((row) =>
                    row.id === u.id ? { ...row, active: !row.active } : row,
                  ),
                });
                notify(
                  `사용자를 ${u.active ? "비활성" : "활성"}으로 변경했습니다`,
                );
              } catch (cause) {
                notify(
                  cause instanceof Error
                    ? cause.message
                    : "사용자 상태 저장에 실패했습니다",
                );
              }
            }}
          >
            {u.active ? "비활성으로 변경" : "활성으로 변경"}
          </button>
          <button className="button primary" onClick={close}>
            확인
          </button>
        </div>
      </Modal>
    );
  }
  if (detail.type === "facility") {
    // 목록에서 이미 불러온 기관을 그대로 받는다 (수만 건이라 store에 전체가 없다)
    const f = detail.facility;
    return (
      <Modal title="의료기관 상세" close={close}>
        <div className="detail-title">
          <span className="detail-icon">{f.type === "약국" ? "💊" : "🏥"}</span>
          <div>
            <h3>{f.name}</h3>
            <p>
              {f.id} · {f.type}
            </p>
          </div>
        </div>
        <dl className="details-grid">
          <div>
            <dt>지역</dt>
            <dd>{f.region}</dd>
          </div>
          <div>
            <dt>상태</dt>
            <dd>
              <Badge tone={f.active ? "green" : "gray"}>
                {f.active ? "운영 중" : "중지·숨김"}
              </Badge>
            </dd>
          </div>
          <div>
            <dt>야간진료</dt>
            <dd>
              <Badge tone={f.night ? "purple" : "gray"}>
                {f.night ? "가능" : "없음"}
              </Badge>
            </dd>
          </div>
          <div>
            <dt>응급실</dt>
            <dd>
              <Badge tone={f.emergency ? "red" : "gray"}>
                {f.emergency ? "있음" : "없음"}
              </Badge>
            </dd>
          </div>
          <div>
            <dt>주소</dt>
            <dd>{f.address || "-"}</dd>
          </div>
          <div>
            <dt>전화</dt>
            <dd>{f.phone || "-"}</dd>
          </div>
          <div>
            <dt>출처</dt>
            <dd>{f.source || "-"}</dd>
          </div>
          <div>
            <dt>확인일</dt>
            <dd>{f.verifiedOn || "-"}</dd>
          </div>
        </dl>
        <div className="info-note">
          <AlertCircle size={17} />
          <p>기관명과 운영 정보는 백엔드 의료기관 데이터입니다</p>
        </div>
        <div className="modal-actions">
          <button className="button primary" onClick={close}>
            확인
          </button>
        </div>
      </Modal>
    );
  }
  const inquiry = store.inquiries.find((q) => q.id === detail.id);
  if (!inquiry) return null;
  return (
    <InquiryDetails
      key={inquiry.id}
      inquiry={inquiry}
      close={close}
      open={open}
      // 목록 응답에는 문의 본문·답변이 없으므로 모달을 열 때 상세를 불러와 반영
      load={async () => {
        try {
          const fresh = await getInquiry(inquiry.id);
          update({
            ...store,
            inquiries: store.inquiries.map((row) =>
              row.id === fresh.id ? { ...row, ...fresh } : row,
            ),
          });
        } catch (cause) {
          notify(
            cause instanceof Error
              ? cause.message
              : "문의 상세를 불러오지 못했습니다",
          );
        }
      }}
      // 저장 성공 시 빈 문자열, 실패 시 오류 문구를 반환
      save={async (reply, requestId) => {
        try {
          const value = reply.trim();
          if (!value) return "답변을 입력해 주세요.";
          const saved = await answerInquiry(
            inquiry.id,
            inquiry.version ?? 0,
            value,
            requestId,
          );
          update({
            ...store,
            inquiries: store.inquiries.map((row) =>
              row.id === saved.id
                ? { ...row, ...saved, content: saved.content || row.content }
                : row,
            ),
          });
          notify("답변을 저장했습니다");
          return "";
        } catch (cause) {
          return cause instanceof Error
            ? cause.message
            : "답변 저장에 실패했습니다";
        }
      }}
    />
  );
}

function InquiryDetails({
  inquiry: q,
  close,
  open,
  load,
  save,
}: {
  inquiry: Inquiry;
  close: () => void;
  open: OpenDetail;
  load: () => Promise<void>;
  save: (text: string, requestId: string) => Promise<string>;
}) {
  const [draft, setDraft] = useState("");
  const [error, setError] = useState("");
  // 같은 내용을 다시 보내는 재시도는 같은 요청 ID를 써서 서버가 중복 등록으로 처리하지 않게 한다
  const pending = useRef<{ content: string; id: string } | null>(null);
  const answered = Boolean(q.reply.trim());
  useEffect(() => {
    void load();
    // 모달이 열릴 때 한 번만 불러온다
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [q.id]);
  async function submit(e: FormEvent) {
    e.preventDefault();
    const value = draft.trim();
    if (!value) {
      setError("답변을 입력해 주세요.");
      return;
    }
    if (pending.current?.content !== value)
      pending.current = { content: value, id: crypto.randomUUID() };
    setError(await save(value, pending.current.id));
  }
  return (
    <Modal title={`문의 ${q.id} 상세`} close={close} wide>
      <div className="inquiry-meta">
        <Badge tone="blue">{q.category}</Badge>
        <Badge tone={q.reply.trim() ? "green" : "orange"}>
          {q.reply.trim() ? "답변 완료" : "답변 대기"}
        </Badge>
        <span>{q.date.replaceAll("-", ".")}</span>
      </div>
      <h3 className="inquiry-subject">{q.subject}</h3>
      <p className="inquiry-author">
        {q.author} · {q.userId}
      </p>
      <div className="inquiry-body">{q.content}</div>
      {q.diagnosisId && (
        <button
          className="related-diagnosis"
          onClick={() => open({ type: "diagnosis", id: q.diagnosisId! })}
        >
          <FileText size={16} />
          관련 분석 기록 {q.diagnosisId}
          <ExternalLink size={14} />
        </button>
      )}
      {answered ? (
        <>
          <h4 className="section-label">등록된 답변</h4>
          <div className="inquiry-body">{q.reply}</div>
          <p className="detail-footnote">등록한 답변은 수정할 수 없습니다.</p>
          <div className="modal-actions">
            <button type="button" className="button secondary" onClick={close}>
              닫기
            </button>
          </div>
        </>
      ) : (
        <form onSubmit={submit}>
          <label className="form-label" htmlFor="inquiry-reply">
            관리자 답변
          </label>
          <textarea
            id="inquiry-reply"
            placeholder="사용자 문의에 대한 답변을 입력하세요."
            rows={5}
            maxLength={2000}
            value={draft}
            onChange={(e) => {
              setDraft(e.target.value);
              setError("");
            }}
          />
          <div className="reply-hint">
            <span>등록한 답변은 수정할 수 없습니다.</span>
            <span>{draft.length} / 2,000</span>
          </div>
          {error && (
            <p className="form-error" role="alert">
              {error}
            </p>
          )}
          <div className="modal-actions">
            <button type="button" className="button secondary" onClick={close}>
              닫기
            </button>
            <button type="submit" className="button primary">
              <Check size={16} />
              답변 저장
            </button>
          </div>
        </form>
      )}
    </Modal>
  );
}

const SYNC_SCOPES: [string, string][] = [
  ["ALL", "병원 + 약국 전체"],
  ["HOSPITAL", "병원만"],
  ["PHARMACY", "약국만"],
];

// 공공데이터 의료기관 동기화: 실행 → 결과 표시 → 기관 목록·집계 갱신
function SyncDetails({
  store,
  update,
  close,
  notify,
  onFacilitiesChanged,
}: {
  store: Store;
  update: (store: Store) => void;
  close: () => void;
  notify: (s: string) => void;
  onFacilitiesChanged: () => void;
}) {
  const [scope, setScope] = useState("ALL");
  const [running, setRunning] = useState(false);
  const [error, setError] = useState("");
  const [result, setResult] = useState<HospitalSyncResult | null>(null);
  async function run() {
    setRunning(true);
    setError("");
    setResult(null);
    try {
      const done = await syncHospitals(scope);
      setResult(done);
      notify("의료기관 동기화를 완료했습니다");
      // 동기화는 끝났으므로, 뒤 화면 갱신이 실패해도 동기화 실패로 보이면 안 된다
      try {
        // 화면 전체를 다시 불러오지 않고 집계만 갱신하고, 목록은 화면이 서버에서 다시 불러오게 한다 (결과 모달이 닫히지 않도록)
        const dashboard = await getDashboard();
        update({ ...store, dashboard: { ...store.dashboard, ...dashboard } });
      } catch {
        // 집계는 다음에 화면을 열 때 갱신된다
      }
      onFacilitiesChanged();
    } catch (cause) {
      setError(
        cause instanceof Error ? cause.message : "동기화에 실패했습니다",
      );
    } finally {
      setRunning(false);
    }
  }
  const m = result?.metrics;
  return (
    // 실행 중에는 닫을 수 없다 (서버 작업은 계속되므로 결과를 확인할 수 있어야 한다)
    <Modal title="공공데이터 동기화" close={running ? () => {} : close}>
      <div className="modal-callout">
        <span>🏥</span>
        <div>
          <h3>공공데이터 기준으로 의료기관을 갱신합니다</h3>
          <p>
            신규 기관을 추가하고, 바뀐 정보를 갱신하고, 원본에서 사라진 기관은
            운영 중지합니다.
          </p>
        </div>
      </div>
      <div className="info-note">
        <AlertCircle size={18} />
        <p>
          병원은 건강보험심사평가원에서 피부과 진료과목을 가진 기관을, 약국은
          국립중앙의료원에서 가져오며 병원에는 진료시간과 응급실 정보를
          결합합니다. 관리자가 직접 등록하거나 숨김 처리한 기관은 바뀌지 않고,
          동기화된 기관은 출처와 확인일이 기록되어 사용자 검색에 노출됩니다.
          전체 동기화는 1분 안팎이 걸릴 수 있으니 완료될 때까지 기다려 주세요.
        </p>
      </div>
      <label className="form-label">
        동기화 대상
        <select
          aria-label="동기화 대상"
          value={scope}
          disabled={running}
          onChange={(e) => setScope(e.target.value)}
        >
          {SYNC_SCOPES.map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
      </label>
      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}
      {m && result && (
        <dl className="details-grid" aria-label="동기화 결과">
          <div>
            <dt>수집</dt>
            <dd>{m.totalFetched.toLocaleString()}건</dd>
          </div>
          <div>
            <dt>신규 추가</dt>
            <dd>{m.newAdded.toLocaleString()}건</dd>
          </div>
          <div>
            <dt>정보 갱신</dt>
            <dd>{m.updated.toLocaleString()}건</dd>
          </div>
          <div>
            <dt>운영 중지</dt>
            <dd>{m.deactivated.toLocaleString()}건</dd>
          </div>
          <div>
            <dt>소요 시간</dt>
            <dd>{result.durationSeconds.toFixed(1)}초</dd>
          </div>
          <div>
            <dt>작업 번호</dt>
            <dd>{result.syncId}</dd>
          </div>
        </dl>
      )}
      <div className="modal-actions">
        <button className="button secondary" onClick={close} disabled={running}>
          닫기
        </button>
        <button
          className="button primary"
          onClick={() => void run()}
          disabled={running}
        >
          {running ? "동기화 중…" : result ? "다시 동기화" : "동기화 시작"}
        </button>
      </div>
    </Modal>
  );
}
