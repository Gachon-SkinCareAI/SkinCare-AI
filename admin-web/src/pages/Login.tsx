import { useState, type FormEvent } from 'react'
import { ArrowRight, Eye, EyeOff, LockKeyhole, ShieldCheck } from 'lucide-react'
import { login } from '../services/api'

const REMEMBERED_ID_KEY = 'skincare-admin-remembered-id'

export default function Login({ onSuccess }: { onSuccess: () => void }) {
  const [adminId, setAdminId] = useState(() => localStorage.getItem(REMEMBERED_ID_KEY) || '')
  const [password, setPassword] = useState('')
  const [rememberId, setRememberId] = useState(() => Boolean(localStorage.getItem(REMEMBERED_ID_KEY)))
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState('')
  const [fieldErrors, setFieldErrors] = useState({ adminId: '', password: '' })
  const [pending, setPending] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (pending) return

    const nextErrors = {
      adminId: adminId.trim() ? '' : '관리자 아이디를 입력해 주세요.',
      password: password ? '' : '비밀번호를 입력해 주세요.',
    }
    setFieldErrors(nextErrors)
    setError('')
    if (nextErrors.adminId || nextErrors.password) return

    setPending(true)
    try {
      await login(adminId.trim(), password)
      // 아이디만 선택적으로 기억하고 비밀번호나 토큰은 이 설정에 저장하지 않습니다.
      if (rememberId) localStorage.setItem(REMEMBERED_ID_KEY, adminId.trim())
      else localStorage.removeItem(REMEMBERED_ID_KEY)
      onSuccess()
    } catch (cause) {
      setError(
        cause instanceof Error && cause.message === '서버에 연결할 수 없습니다'
          ? '서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.'
          : '아이디 또는 비밀번호가 올바르지 않습니다. 다시 확인해 주세요.',
      )
    } finally {
      setPending(false)
    }
  }

  return (
    <main className="login-page">
      <div className="login-layout">
        <section className="login-intro" aria-label="관리자 시스템 소개">
          <div className="login-brand">
            <img src={`${import.meta.env.BASE_URL}123.png`} alt="SkinCare AI" />
            <span>SkinCare AI</span>
          </div>
          <div className="login-intro-copy">
            <span className="login-eyebrow">ADMIN CONSOLE</span>
            <h1>더 안전한 진료 연결을 위한<br />관리자 시스템</h1>
            <p>진단 현황부터 사용자 및 의료기관 관리까지, 필요한 운영 정보를 한곳에서 확인하세요.</p>
          </div>
          <div className="login-intro-footer"><ShieldCheck size={17} /> 승인된 관리자만 접근할 수 있습니다.</div>
        </section>

        <section className="login-form-area" aria-labelledby="login-heading">
          <div className="login-form-heading">
            <span className="login-lock"><LockKeyhole size={23} /></span>
            <h2 id="login-heading">관리자 로그인</h2>
            <p>등록된 관리자 계정으로 로그인해 주세요.</p>
          </div>

          <form className="login-form" onSubmit={(event) => void submit(event)} noValidate>
            <div className="login-field">
              <label htmlFor="admin-id">관리자 아이디</label>
              <input
                id="admin-id"
                name="adminId"
                autoComplete="username"
                maxLength={36}
                placeholder="아이디를 입력해 주세요"
                value={adminId}
                aria-invalid={Boolean(fieldErrors.adminId)}
                aria-describedby={fieldErrors.adminId ? 'admin-id-error' : undefined}
                onChange={(event) => {
                  setAdminId(event.target.value)
                  setFieldErrors((current) => ({ ...current, adminId: '' }))
                  setError('')
                }}
              />
              {fieldErrors.adminId && <small id="admin-id-error" className="login-field-error">{fieldErrors.adminId}</small>}
            </div>

            <div className="login-field">
              <label htmlFor="admin-password">비밀번호</label>
              <div className="login-password-wrap">
                <input
                  id="admin-password"
                  name="password"
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="current-password"
                  placeholder="비밀번호를 입력해 주세요"
                  value={password}
                  aria-invalid={Boolean(fieldErrors.password)}
                  aria-describedby={fieldErrors.password ? 'admin-password-error' : undefined}
                  onChange={(event) => {
                    setPassword(event.target.value)
                    setFieldErrors((current) => ({ ...current, password: '' }))
                    setError('')
                  }}
                />
                <button
                  type="button"
                  className="login-password-toggle"
                  aria-label={showPassword ? '비밀번호 숨기기' : '비밀번호 표시'}
                  aria-pressed={showPassword}
                  onClick={() => setShowPassword((current) => !current)}
                >
                  {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </div>
              {fieldErrors.password && <small id="admin-password-error" className="login-field-error">{fieldErrors.password}</small>}
            </div>

            <label className="login-remember">
              <input type="checkbox" checked={rememberId} onChange={(event) => setRememberId(event.target.checked)} />
              아이디 기억하기
            </label>

            {error && <p className="login-request-error" role="alert">{error}</p>}
            <button className="login-submit" type="submit" disabled={pending}>
              {pending ? '로그인 중...' : '로그인'}
              {!pending && <ArrowRight size={18} />}
            </button>
          </form>

          <p className="login-footnote">계정 발급 및 접근 권한은 시스템 관리자에게 문의해 주세요.</p>
        </section>
      </div>
    </main>
  )
}
