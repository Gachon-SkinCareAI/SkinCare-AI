import { useState, type FormEvent } from 'react'
import {
  AlertCircle,
  Check,
  ExternalLink,
  FileText,
  ShieldCheck,
} from 'lucide-react'
import { Badge, Modal, Progress, RiskBadge } from './components'
import {
  saveReply,
  type Detail,
  type Inquiry,
  type OpenDetail,
  type Store,
} from './data'

type Props = {
  detail: Detail
  store: Store
  update: (store: Store) => void
  close: () => void
  open: OpenDetail
  notify: (s: string) => void
}
export default function Details({
  detail,
  store,
  update,
  close,
  open,
  notify,
}: Props) {
  if (detail.type === 'sync')
    return (
      <Modal title="OpenAPI 연동 안내" close={close}>
        <div className="modal-callout">
          <span>🏥</span>
          <div>
            <h3>의료기관 데이터 연결을 준비 중입니다</h3>
            <p>
              현재 목록은 와이어프레임 확인을 위한 가상 의료기관입니다. 동기화
              요청은 실제 기관에 전송되지 않습니다.
            </p>
          </div>
        </div>
        <div className="info-note">
          <AlertCircle size={18} />
          <p>
            실제 동기화는 공공데이터포털 API가 연결된 백엔드가 필요합니다. API
            키는 프론트엔드에 저장하지 않고 서버에서 관리해야 합니다.
          </p>
        </div>
        <div className="modal-actions">
          <button className="button primary" onClick={close}>
            확인
          </button>
        </div>
      </Modal>
    )
  if (detail.type === 'diagnosis') {
    const d = store.diagnoses.find((row) => row.id === detail.id)
    if (!d) return null
    const user = store.users.find((u) => u.id === d.userId)!
    return (
      <Modal title="진단 상세" close={close}>
        <div className="detail-title">
          <span className="detail-icon">📋</span>
          <div>
            <h3>{d.id}</h3>
            <p>2026.09.29 · {d.time}</p>
          </div>
          <RiskBadge risk={d.risk} />
        </div>
        <dl className="details-grid">
          <div>
            <dt>사용자</dt>
            <dd>
              <button
                className="table-link"
                onClick={() => open({ type: 'user', id: user.id })}
              >
                {user.name} · {user.id}
              </button>
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
              <Badge tone={d.status === '진행중' ? 'blue' : 'green'}>
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
            onChange={(e) => {
              update({
                ...store,
                diagnoses: store.diagnoses.map((row) =>
                  row.id === d.id
                    ? { ...row, status: e.target.value as typeof d.status }
                    : row,
                ),
              })
              notify('샘플 진단의 처리 상태를 저장했습니다.')
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
          <p>
            가상 진단 기록입니다. 상태 변경은 브라우저에만 저장되며 실제
            알림이나 의료기관 연결을 실행하지 않습니다.
          </p>
        </div>
        <div className="modal-actions">
          <button className="button secondary" onClick={close}>
            닫기
          </button>
        </div>
      </Modal>
    )
  }
  if (detail.type === 'user') {
    const u = store.users.find((row) => row.id === detail.id)
    if (!u) return null
    const related = store.diagnoses.filter((d) => d.userId === u.id)
    return (
      <Modal title="사용자 상세" close={close}>
        <div className="detail-title">
          <span className="avatar large">{u.name.slice(0, 1)}</span>
          <div>
            <h3>{u.name}</h3>
            <p>{u.id}</p>
          </div>
          <Badge tone={u.active ? 'green' : 'gray'}>
            {u.active ? '활성' : '비활성'}
          </Badge>
        </div>
        <dl className="details-grid">
          <div>
            <dt>가입일</dt>
            <dd>{u.joinedAt.replaceAll('-', '.')}</dd>
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
              <Badge tone={u.plan === 'Premium' ? 'orange' : 'gray'}>
                {u.plan}
              </Badge>
            </dd>
          </div>
        </dl>
        <h4 className="section-label">관련 샘플 진단</h4>
        {related.length ? (
          <div className="related-list">
            {related.map((d) => (
              <button
                key={d.id}
                onClick={() => open({ type: 'diagnosis', id: d.id })}
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
          <p className="muted detail-empty">연결된 샘플 진단이 없습니다.</p>
        )}
        <p className="detail-footnote">
          상태 변경은 이 브라우저의 데모에만 반영됩니다.
        </p>
        <div className="modal-actions">
          <button
            className="button secondary"
            onClick={() => {
              update({
                ...store,
                users: store.users.map((row) =>
                  row.id === u.id ? { ...row, active: !row.active } : row,
                ),
              })
              notify(
                `사용자를 ${u.active ? '비활성' : '활성'}으로 변경했습니다.`,
              )
            }}
          >
            {u.active ? '비활성으로 변경' : '활성으로 변경'}
          </button>
          <button className="button primary" onClick={close}>
            확인
          </button>
        </div>
      </Modal>
    )
  }
  if (detail.type === 'facility') {
    const f = store.facilities.find((row) => row.id === detail.id)
    if (!f) return null
    return (
      <Modal title="의료기관 상세" close={close}>
        <div className="detail-title">
          <span className="detail-icon">{f.type === '약국' ? '💊' : '🏥'}</span>
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
              <Badge tone={f.active ? 'green' : 'gray'}>
                {f.active ? '활성' : '비활성'}
              </Badge>
            </dd>
          </div>
          <div>
            <dt>야간진료</dt>
            <dd>
              <Badge tone={f.night ? 'purple' : 'gray'}>
                {f.night ? '가능' : '없음'}
              </Badge>
            </dd>
          </div>
          <div>
            <dt>응급실</dt>
            <dd>
              <Badge tone={f.emergency ? 'red' : 'gray'}>
                {f.emergency ? '있음' : '없음'}
              </Badge>
            </dd>
          </div>
        </dl>
        <div className="info-note">
          <AlertCircle size={17} />
          <p>
            기관명과 운영 정보는 가상의 샘플입니다. 실제 진료·약국 이용을 위한
            정보로 사용할 수 없습니다.
          </p>
        </div>
        <div className="modal-actions">
          <button className="button primary" onClick={close}>
            확인
          </button>
        </div>
      </Modal>
    )
  }
  const inquiry = store.inquiries.find((q) => q.id === detail.id)
  if (!inquiry) return null
  return (
    <InquiryDetails
      key={inquiry.id}
      inquiry={inquiry}
      close={close}
      open={open}
      save={(reply) => {
        try {
          update(saveReply(store, inquiry.id, reply))
          notify(
            '답변을 브라우저에 저장했습니다. 실제 메시지는 발송되지 않습니다.',
          )
          return true
        } catch {
          return false
        }
      }}
    />
  )
}

function InquiryDetails({
  inquiry: q,
  close,
  open,
  save,
}: {
  inquiry: Inquiry
  close: () => void
  open: OpenDetail
  save: (text: string) => boolean
}) {
  const [draft, setDraft] = useState(q.reply)
  const [error, setError] = useState('')
  function submit(e: FormEvent) {
    e.preventDefault()
    if (!draft.trim()) {
      setError('답변을 입력해 주세요.')
      return
    }
    if (save(draft)) setError('')
    else setError('답변을 저장할 수 없습니다. 내용을 확인해 주세요.')
  }
  return (
    <Modal title={`문의 ${q.id} 상세`} close={close} wide>
      <div className="inquiry-meta">
        <Badge tone="blue">{q.category}</Badge>
        <Badge tone={q.reply.trim() ? 'green' : 'orange'}>
          {q.reply.trim() ? '답변 완료' : '답변 대기'}
        </Badge>
        <span>{q.date.replaceAll('-', '.')}</span>
      </div>
      <h3 className="inquiry-subject">{q.subject}</h3>
      <p className="inquiry-author">
        {q.author} · {q.userId}
      </p>
      <div className="inquiry-body">{q.content}</div>
      {q.diagnosisId && (
        <button
          className="related-diagnosis"
          onClick={() => open({ type: 'diagnosis', id: q.diagnosisId! })}
        >
          <FileText size={16} />
          관련 분석 기록 {q.diagnosisId}
          <ExternalLink size={14} />
        </button>
      )}
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
            setDraft(e.target.value)
            setError('')
          }}
        />
        <div className="reply-hint">
          <span>데모 저장만 지원하며 실제 발송은 하지 않습니다.</span>
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
            {q.reply.trim() ? '답변 수정 저장' : '답변 저장'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
