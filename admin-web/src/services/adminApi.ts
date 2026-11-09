import { request } from './api'
import type { PageResponse } from '../types/admin'
import type { Facility, Inquiry, User, Diagnosis, MonitoringData } from '../data'

type Wrapped<T> = { data?: T; [key: string]: unknown }
type RecordValue = Record<string, unknown>

// 중첩된 백엔드 데이터 필드를 추출
function unwrap<T>(body: Wrapped<T>, key: string): T {
  if (body[key] !== undefined) return body[key] as T
  if (body.data !== undefined) return body.data as T
  return body as unknown as T
}

// 페이지 응답을 공통 형태로 변환
function page<T>(body: unknown, key: string): PageResponse<T> {
  if (body && typeof body === 'object' && 'items' in body) return body as PageResponse<T>
  const wrapped = (body || {}) as Wrapped<T[]>
  const items = (unwrap<T[]>(wrapped, key) || []) as T[]
  return {
    items,
    page: Number(wrapped.current_page || 1) - 1,
    size: Number(wrapped.page_size || items.length || 20),
    hasNext: Number(wrapped.current_page || 1) < Number(wrapped.total_pages || 1),
    total: Number(wrapped.total_count || items.length),
  }

}

// 백엔드 위험도 코드를 화면 문구로 변환
function risk(value: unknown): '응급' | '경고' | '관찰' {
  return value === 'EMERGENCY' ? '응급' : value === 'WARNING' ? '경고' : '관찰'
}

// 사용자 API 응답을 화면 모델로 변환
function mapUser(value: unknown): User {
  const row = value as RecordValue
  return { id: String(row.userId || ''), name: String(row.nickname || row.userName || ''), joinedAt: String(row.joinedAt || ''), diagnoses: Number(row.totalDiagnoses || 0), risk: risk(row.latestRisk), plan: row.plan === 'PREMIUM' ? 'Premium' : 'Free', active: row.status === 'ACTIVE' }
}

// 진단 API 응답을 화면 모델로 변환
function mapDiagnosis(value: unknown): Diagnosis {
  const row = value as RecordValue
  return { id: String(row.diagnosisId || ''), userId: String(row.userId || ''), time: String(row.createdAt || ''), condition: String(row.suspectedDisease || row.aiLabel || ''), area: String(row.bodyPart || ''), risk: risk(row.riskLevel), confidence: Number(row.confidenceScore || 0), status: String(row.statusLabel || row.status || ''), connection: String(row.statusLabel || '') }
}

// 의료기관 API 응답을 화면 모델로 변환
function mapFacility(value: unknown): Facility {
  const row = value as RecordValue
  return { id: String(row.id || ''), name: String(row.name || ''), type: String(row.type || ''), region: String(row.region || ''), night: Boolean(row.nightCare), emergency: Boolean(row.emergency), active: row.status === 'ACTIVE', address: String(row.address || ''), phone: String(row.tel || ''), version: Number(row.version || 0) }
}

// 문의 API 응답을 화면 모델로 변환
function mapInquiry(value: unknown): Inquiry {
  const row = value as RecordValue
  const answer = row.answer as RecordValue | null | undefined
  return { id: String(row.inquiryId || row.id || ''), category: String(row.inquiryTypeLabel || row.inquiryType || row.type || ''), userId: String(row.userId || ''), author: String(row.userName || ''), subject: String(row.title || ''), content: String(row.content || ''), diagnosisId: row.relatedDiagnosisId ? String(row.relatedDiagnosisId) : null, date: String(row.createdAt || ''), reply: answer && answer.content ? String(answer.content) : '' }
}

// 대시보드 중첩 지표를 화면 모델로 변환
function mapDashboard(value: RecordValue) {
  const result: Record<string, number> = {}
  for (const [key, raw] of Object.entries(value)) {
    if (typeof raw === 'number') result[key] = raw
    else if (raw && typeof raw === 'object' && 'count' in raw) result[key.replace('total_users', 'totalUsers').replace('today_diagnoses', 'todayDiagnoses').replace('emergency_cases', 'emergencies')] = Number((raw as RecordValue).count || 0)
  }
  return result
}

// 관리자 대시보드 데이터를 조회
export async function getDashboard() {
  return mapDashboard(await request<RecordValue>('/api/wc/admin/dashboard'))
}

// 관리자 사용자 목록을 조회
export async function getUsers(params: URLSearchParams) {
  const result = page<unknown>(await request(`/api/wc/admin/users?${params}`), 'user_list')
  return { ...result, items: result.items.map(mapUser) }
}

// 관리자 진단 목록을 조회
export async function getDiagnoses(params: URLSearchParams) {
  const result = page<unknown>(await request(`/api/wc/admin/diagnoses?${params}`), 'diagnoses_list')
  return { ...result, items: result.items.map(mapDiagnosis) }
}

// 관리자 의료기관 목록을 조회
export async function getFacilities(params: URLSearchParams) {
  const result = page<unknown>(await request(`/api/wc/admin/hospitals?${params}`), 'items')
  return { ...result, items: result.items.map(mapFacility) }
}

// 관리자 문의 목록을 조회
export async function getInquiries(params: URLSearchParams) {
  const result = page<unknown>(await request(`/api/wc/admin/inquiries?${params}`), 'items')
  return { ...result, items: result.items.map(mapInquiry) }
}

// AI 모니터링 정보를 조회
export async function getMonitoring(): Promise<MonitoringData> {
  const [metrics, pipelines, health] = await Promise.all([
    request<Wrapped<Record<string, number>>>('/api/wc/admin/ai/metrics'),
    request<Wrapped<unknown[]>>('/api/wc/admin/ai/pipelines'),
    request<Wrapped<Record<string, unknown>>>('/api/wc/admin/ai/health'),
  ])
  return {
    metrics: unwrap(metrics, 'metrics_summary'),
    pipelines: unwrap(pipelines, 'pipeline_status'),
    health: unwrap(health, 'ai_health'),
  }
}

// 전체 관리자 화면 데이터를 조회
export async function getAdminStore() {
  // API별 페이지 시작 번호와 최대 크기가 달라 요청을 분리
  const legacyParams = new URLSearchParams({ page: '1', size: '50' })
  const serviceParams = new URLSearchParams({ page: '0', size: '50' })
  const [dashboard, users, diagnoses, facilities, inquiries, monitoring] =
    await Promise.all([
      getDashboard(),
      getUsers(legacyParams),
      getDiagnoses(legacyParams),
      getFacilities(serviceParams),
      getInquiries(serviceParams),
      getMonitoring(),
    ])
  return {
    dashboard,
    users: users.items,
    diagnoses: diagnoses.items,
    facilities: facilities.items,
    inquiries: inquiries.items,
    monitoring,
  }
}

// 문의 답변을 저장
export function answerInquiry(id: string, content: string) {
  return request<Inquiry>(`/api/wc/admin/inquiries/${id}/answer`, {
    method: 'PUT',
    body: JSON.stringify({ content }),
  })
}

// 진단 처리 상태를 저장
export function updateDiagnosisStatus(id: string, status: string) {
  const statusMap: Record<string, string> = { 진행중: 'IN_PROGRESS', 알림발송: 'ALERT_SENT', 완료: 'COMPLETED', 처리완료: 'COMPLETED' }
  return request(`/api/wc/admin/diagnoses/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status: statusMap[status] || status, reviewStatus: 'NORMAL' }),
  })
}

// 사용자 계정 상태를 저장
export function updateUserStatus(id: string, active: boolean) {
  return request(`/api/wc/admin/users/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status: active ? 'ACTIVE' : 'INACTIVE', reason: '관리자 화면 변경' }),
  })
}

// AI 재학습 이력을 조회
export function getRetrainHistory(pageNumber = 1, limit = 10) {
  return request(`/api/wc/admin/ai/retrain-history?page=${pageNumber}&limit=${limit}`)
}

// AI 재학습을 요청
export function requestRetrain(reason: string) {
  return request('/api/wc/admin/ai/retrain', {
    method: 'POST',
    body: JSON.stringify({ reason }),
  })
}

// 재학습 작업 상태를 조회
export function getRetrainJob(jobId: string) {
  return request(`/api/wc/admin/ai/jobs/${encodeURIComponent(jobId)}`)
}

// 검증된 모델 배포를 승인
export function approveRetrainJob(jobId: string) {
  return request(`/api/wc/admin/ai/jobs/${encodeURIComponent(jobId)}/approve`, { method: 'POST' })
}

// 운영 분석 목록과 준비 상태를 조회
export function getOperations(status?: string, pageNumber = 0, size = 20) {
  const query = new URLSearchParams({ page: String(pageNumber), size: String(size) })
  if (status) query.set('status', status)
  return request(`/api/wc/admin/operations?${query}`)
}

// AI 서버 상태를 조회
export function getAiStatus() {
  return request('/api/wc/admin/operations/ai-status')
}

// 서비스 준비 상태를 조회
export function getReadiness() {
  return request('/api/wc/admin/operations/readiness')
}
