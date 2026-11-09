import { risks, type Page, type Risk } from '../data'

export const pages: { id: Page; title: string; icon: string }[] = [
  { id: 'dashboard', title: '대시보드', icon: '📊' },
  { id: 'diagnoses', title: '진단 로그', icon: '📋' },
  { id: 'monitoring', title: 'AI 모니터링', icon: '🤖' },
  { id: 'facilities', title: '의료기관 관리', icon: '🏥' },
  { id: 'users', title: '사용자 관리', icon: '👤' },
  { id: 'inquiries', title: '문의 관리', icon: '✉' },
]

// 관리자 웹의 기준 경로
export const ADMIN_BASE_PATH = '/admin'

// 현재 브라우저 경로에서 화면 정보를 읽기
export function getRoute() {
  const path = location.pathname.replace(new RegExp(`^${ADMIN_BASE_PATH}`), '')
  const [segment, query] = `${path}${location.search}`.replace(/^\/+/, '').split('?')
  const id = segment === 'analyses' ? 'diagnoses' : segment
  const page: Page = pages.find((p) => p.id === id)?.id || 'dashboard'
  const risk = new URLSearchParams(query).get('risk')
  return {
    page,
    risk: risks.includes(risk as Risk) ? (risk as Risk) : undefined,
  }
}
