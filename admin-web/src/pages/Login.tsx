import { FormEvent, useState } from 'react'
import { login } from '../services/api'

// 관리자 로그인 화면
export default function Login({ onSuccess }: { onSuccess: () => void }) {
  const [adminId, setAdminId] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [fieldErrors, setFieldErrors] = useState({ adminId: '', password: '' })
  const [pending, setPending] = useState(false)

  // 로그인 요청을 처리
  async function submit(event: FormEvent) {
    event.preventDefault()
    const nextErrors = {
      adminId: adminId.trim() ? '' : '아이디를 입력해 주세요.',
      password: password ? '' : '비밀번호를 입력해 주세요.',
    }
    setFieldErrors(nextErrors)
    if (nextErrors.adminId || nextErrors.password) return
    setPending(true)
    setError('')
    setSuccess('')
    try {
      await login(adminId.trim(), password)
      onSuccess()
    } catch (cause) {
      setError('아이디 또는 비밀번호가 올바르지 않습니다. 입력한 정보를 다시 확인해 주세요.')
    } finally {
      setPending(false)
    }
  }

  return (
    <main className="login-page">
      <form className="panel login-card" onSubmit={submit}>
        <h1>SkinCare AI 관리자</h1>
        <p>관리자 계정으로 로그인해 주세요</p>
        <label className="form-label">관리자 ID<input value={adminId} aria-invalid={Boolean(fieldErrors.adminId)} onChange={(e) => { setAdminId(e.target.value); setFieldErrors((current) => ({ ...current, adminId: '' })) }} />{fieldErrors.adminId && <small className="form-error">{fieldErrors.adminId}</small>}</label>
        <label className="form-label">비밀번호<input type="password" value={password} aria-invalid={Boolean(fieldErrors.password)} onChange={(e) => { setPassword(e.target.value); setFieldErrors((current) => ({ ...current, password: '' })) }} />{fieldErrors.password && <small className="form-error">{fieldErrors.password}</small>}</label>
        {error && <p role="alert" className="form-error">{error}</p>}
        {success && <p role="status" className="login-success">{success}</p>}
        <button className="button primary" disabled={pending}>{pending ? '로그인 중' : '로그인'}</button>
      </form>
    </main>
  )
}
