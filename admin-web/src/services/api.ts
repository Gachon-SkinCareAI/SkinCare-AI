import type { AdminSession } from '../types/admin'

const API_BASE = import.meta.env.VITE_API_BASE_URL || ''
const TOKEN_KEY = 'skincare-admin-token'

// 인증 토큰을 저장
export function saveSession(session: AdminSession) {
  localStorage.setItem(TOKEN_KEY, session.token)
  localStorage.setItem('skincare-admin-name', session.name)
  localStorage.setItem('skincare-admin-id', session.adminId)
}

// 인증 토큰을 제거
export function clearSession() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem('skincare-admin-name')
  localStorage.removeItem('skincare-admin-id')
}

// 현재 인증 여부를 확인
export function hasSession() {
  return Boolean(localStorage.getItem(TOKEN_KEY))
}

// 백엔드 API를 호출하고 오류를 표준화
export async function request<T>(
  path: string,
  init: RequestInit = {},
): Promise<T> {
  const token = localStorage.getItem(TOKEN_KEY)
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  if (init.body && !headers.has('Content-Type'))
    headers.set('Content-Type', 'application/json')
  if (token) headers.set('Authorization', `Bearer ${token}`)

  let response: Response
  try {
    response = await fetch(`${API_BASE}${path}`, { ...init, headers })
  } catch {
    throw new Error('서버에 연결할 수 없습니다')
  }

  const text = await response.text()
  let body: unknown = null
  if (text) {
    try {
      body = JSON.parse(text)
    } catch {
      body = text
    }
  }
  if (response.status === 401 || response.status === 403) {
    clearSession()
    throw new Error('로그인이 만료되었거나 관리자 권한이 없습니다')
  }
  if (!response.ok) {
    const message =
      typeof body === 'object' && body && 'resultMsg' in body
        ? String(body.resultMsg)
        : '요청을 처리하지 못했습니다'
    throw new Error(message)
  }
  return body as T
}

// 관리자 로그인
export async function login(adminId: string, password: string) {
  const session = await request<AdminSession>('/api/wc/admin/auth/login', {
    method: 'POST',
    body: JSON.stringify({ adminId, password }),
  })
  saveSession(session)
  return session
}

// 관리자 로그아웃
export async function logout() {
  try {
    await request('/api/wc/admin/auth/logout', { method: 'POST' })
  } finally {
    clearSession()
  }
}
