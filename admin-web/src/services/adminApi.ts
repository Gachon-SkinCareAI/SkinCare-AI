import { request } from "./api";
import type { PageResponse } from "../types/admin";
import {
  dayKey,
  type AiHealth,
  type AiMetric,
  type AiPipeline,
  type Facility,
  type Inquiry,
  type User,
  type Diagnosis,
  type MonitoringData,
} from "../data";

type Wrapped<T> = { data?: T; [key: string]: unknown };
type RecordValue = Record<string, unknown>;

// 중첩된 백엔드 데이터 필드를 추출
function unwrap<T>(body: Wrapped<T>, key: string): T {
  if (body[key] !== undefined) return body[key] as T;
  if (body.data !== undefined) return body.data as T;
  return body as unknown as T;
}

// 페이지 응답을 공통 형태로 변환
function page<T>(body: unknown, key: string): PageResponse<T> {
  if (body && typeof body === "object" && "items" in body)
    return body as PageResponse<T>;
  const wrapped = (body || {}) as Wrapped<T[]>;
  const items = (unwrap<T[]>(wrapped, key) || []) as T[];
  return {
    items,
    page: Number(wrapped.current_page || 1) - 1,
    size: Number(wrapped.page_size || items.length || 20),
    hasNext:
      Number(wrapped.current_page || 1) < Number(wrapped.total_pages || 1),
    total: Number(wrapped.total_count || items.length),
  };
}

// 백엔드 위험도 코드를 화면 문구로 변환
function risk(value: unknown): "응급" | "경고" | "관찰" {
  return value === "EMERGENCY" ? "응급" : value === "WARNING" ? "경고" : "관찰";
}

// 사용자 API 응답을 화면 모델로 변환
function mapUser(value: unknown): User {
  const row = value as RecordValue;
  return {
    id: String(row.userId || ""),
    name: String(row.nickname || row.userName || ""),
    joinedAt: String(row.joinedAt || ""),
    diagnoses: Number(row.totalDiagnoses || 0),
    risk: risk(row.latestRisk),
    plan: row.plan === "PREMIUM" ? "Premium" : "Free",
    active: row.status === "ACTIVE",
  };
}

// 진단 API 응답을 화면 모델로 변환
function mapDiagnosis(value: unknown): Diagnosis {
  const row = value as RecordValue;
  return {
    id: String(row.diagnosisId || ""),
    userId: String(row.userId || ""),
    time: String(row.createdAt || ""),
    condition: String(row.suspectedDisease || row.aiLabel || ""),
    area: String(row.bodyPart || ""),
    risk: risk(row.riskLevel),
    confidence: Number(row.confidenceScore || 0),
    status: String(row.statusLabel || row.status || ""),
    connection: String(row.statusLabel || ""),
  };
}

// 백엔드 의료기관 종류 코드를 화면 문구로 변환 (종류 필터와 아이콘이 이 문구를 기준으로 동작)
const FACILITY_TYPES: Record<string, string> = {
  DERMATOLOGY: "피부과",
  GENERAL_HOSPITAL: "종합병원",
  PHARMACY: "약국",
  OTHER: "병원/의원",
};

// 의료기관 API 응답을 화면 모델로 변환
function mapFacility(value: unknown): Facility {
  const row = value as RecordValue;
  return {
    id: String(row.id || ""),
    name: String(row.name || ""),
    type: FACILITY_TYPES[String(row.type)] ?? String(row.type || ""),
    region: String(row.region || ""),
    night: Boolean(row.nightCare),
    emergency: Boolean(row.emergency),
    active: row.status === "ACTIVE",
    address: String(row.address || ""),
    phone: String(row.tel || ""),
    version: Number(row.version || 0),
    source: row.dataSource ? String(row.dataSource) : undefined,
    verifiedOn: row.verifiedOn ? String(row.verifiedOn) : undefined,
  };
}

// 목록 API는 답변 본문을 주지 않으므로, 완료 문의는 상세를 열 때 실제 답변으로 교체되는 표시용 문구를 둔다
export const ANSWERED_PLACEHOLDER = "답변이 등록된 문의입니다.";

// 서버의 ISO 일시(2026-11-10T19:50:08.65)를 화면용 "2026-11-10 19:50"으로 변환
function formatDateTime(value: unknown) {
  return String(value || "")
    .replace("T", " ")
    .slice(0, 16);
}

// 문의 API 응답을 화면 모델로 변환
function mapInquiry(value: unknown): Inquiry {
  const row = value as RecordValue;
  const answer = row.answer as RecordValue | null | undefined;
  const reply =
    answer && answer.content
      ? String(answer.content)
      : row.status === "COMPLETED"
        ? ANSWERED_PLACEHOLDER
        : "";
  return {
    id: String(row.inquiryId || row.id || ""),
    category: String(row.inquiryTypeLabel || row.inquiryType || row.type || ""),
    userId: String(row.userId || ""),
    author: String(row.userName || ""),
    subject: String(row.title || ""),
    content: String(row.content || ""),
    diagnosisId: row.relatedDiagnosisId
      ? String(row.relatedDiagnosisId)
      : row.analysisId
        ? String(row.analysisId)
        : null,
    date: formatDateTime(row.createdAt),
    reply,
    version: Number(row.version || 0),
  };
}

// 대시보드 중첩 지표를 화면 모델로 변환
function mapDashboard(value: RecordValue) {
  const result: Record<string, number> = {};
  for (const [key, raw] of Object.entries(value)) {
    if (typeof raw === "number") result[key] = raw;
    else if (raw && typeof raw === "object" && "count" in raw)
      result[
        key
          .replace("total_users", "totalUsers")
          .replace("today_diagnoses", "todayDiagnoses")
          .replace("emergency_cases", "emergencies")
      ] = Number((raw as RecordValue).count || 0);
  }
  return result;
}

// 관리자 대시보드 데이터를 조회
export async function getDashboard() {
  return mapDashboard(await request<RecordValue>("/api/wc/admin/dashboard"));
}

// 관리자 사용자 목록을 조회
export async function getUsers(params: URLSearchParams) {
  const body = await request<RecordValue>(`/api/wc/admin/users?${params}`);
  const result = page<unknown>(body, "user_list");
  return {
    ...result,
    items: result.items.map(mapUser),
    stats: mapUserStats(body.stats),
  };
}

// 사용자 통계(stats)를 화면 지표용 숫자로 변환
function mapUserStats(value: unknown) {
  const stats = (
    value && typeof value === "object" ? value : {}
  ) as RecordValue;
  const count = (key: string) =>
    Number((stats[key] as RecordValue | undefined)?.count ?? 0);
  const premium = stats.premium_users as RecordValue | undefined;
  return {
    premium: count("premium_users"),
    // "28.6%" 형태의 전환율 문자열을 숫자로 변환
    premiumRate:
      Number.parseFloat(String(premium?.conversion_rate ?? "0")) || 0,
    dau: count("dau"),
    wau: count("wau"),
    suspended: Number(stats.suspended_users ?? 0),
  };
}

// 관리자 진단 목록을 조회
export async function getDiagnoses(params: URLSearchParams) {
  const result = page<unknown>(
    await request(`/api/wc/admin/diagnoses?${params}`),
    "diagnoses_list",
  );
  return { ...result, items: result.items.map(mapDiagnosis) };
}

// 의료기관 목록 검색 조건. 서버가 검색·필터·페이지를 처리한다 (동기화 후 수만 건이라 전체를 불러올 수 없다)
export type FacilityQuery = {
  q?: string;
  // 서버 유형 코드: DERMATOLOGY, GENERAL_HOSPITAL, PHARMACY, OTHER (비우면 전체)
  type?: string;
  // ACTIVE(운영 중) 또는 INACTIVE(운영 중지·숨김) (비우면 전체)
  status?: string;
  // 0부터 시작하는 페이지 번호
  page?: number;
  // 한 페이지 건수 (서버 최대 50)
  size?: number;
};

// 관리자 의료기관 목록을 서버에서 검색 (이름순)
export async function searchFacilities(query: FacilityQuery = {}) {
  const params = new URLSearchParams({
    page: String(query.page ?? 0),
    size: String(query.size ?? 20),
  });
  if (query.q?.trim()) params.set("q", query.q.trim());
  if (query.type) params.set("type", query.type);
  if (query.status) params.set("status", query.status);
  const result = page<unknown>(
    await request(`/api/wc/admin/hospitals?${params}`),
    "items",
  );
  return { ...result, items: result.items.map(mapFacility) };
}

// 관리자 문의 목록을 조회
export async function getInquiries(params: URLSearchParams) {
  const result = page<unknown>(
    await request(`/api/wc/admin/inquiries?${params}`),
    "items",
  );
  return { ...result, items: result.items.map(mapInquiry) };
}

// AI 모니터링 정보를 조회
export async function getMonitoring(): Promise<MonitoringData> {
  const [metrics, pipelines, health] = await Promise.all([
    request<Wrapped<Record<string, AiMetric>>>("/api/wc/admin/ai/metrics"),
    request<Wrapped<AiPipeline[]>>("/api/wc/admin/ai/pipelines"),
    request<Wrapped<AiHealth>>("/api/wc/admin/ai/health"),
  ]);
  const summary = unwrap(metrics, "metrics_summary");
  const engines = unwrap(pipelines, "pipeline_status");
  const status = unwrap(health, "ai_health");
  return {
    metrics:
      summary && typeof summary === "object" && !Array.isArray(summary)
        ? summary
        : {},
    pipelines: Array.isArray(engines) ? engines : [],
    health: status && typeof status === "object" ? status : {},
  };
}

// 전체 관리자 화면 데이터를 조회
export async function getAdminStore() {
  // API별 페이지 시작 번호와 최대 크기가 달라 요청을 분리
  const legacyParams = new URLSearchParams({ page: "1", size: "50" });
  const serviceParams = new URLSearchParams({ page: "0", size: "50" });
  // 응급 건수는 서버 집계(total)를 쓰기 위해 1건만 요청
  const emergencyParams = new URLSearchParams({
    page: "1",
    size: "1",
    risk_level: "EMERGENCY",
  });
  const [dashboard, users, diagnoses, emergencies, inquiries, monitoring] =
    await Promise.all([
      getDashboard(),
      getUsers(legacyParams),
      getDiagnoses(legacyParams),
      getDiagnoses(emergencyParams),
      getInquiries(serviceParams),
      getMonitoring(),
    ]);
  // /dashboard는 문의·기관 건수만 제공하므로 사용자·진단 지표는 각 목록 API의 집계로 채운다
  // (오늘 진단 수는 불러온 최근 진단 목록 기준)
  const today = dayKey(new Date());
  return {
    dashboard: {
      ...dashboard,
      ...users.stats,
      totalUsers: users.total,
      emergencies: emergencies.total,
      todayDiagnoses: diagnoses.items.filter(
        (d) => d.time.slice(0, 10) === today,
      ).length,
    },
    users: users.items,
    diagnoses: diagnoses.items,
    inquiries: inquiries.items,
    monitoring,
  };
}

// 공공데이터 의료기관 동기화 결과
export type HospitalSyncResult = {
  syncId: string;
  targetScope: string;
  executedAt: string;
  durationSeconds: number;
  status: string;
  metrics: {
    totalFetched: number;
    newAdded: number;
    updated: number;
    deactivated: number;
  };
};

// 공공데이터로 의료기관을 동기화 (ALL: 병원+약국, HOSPITAL: 병원만, PHARMACY: 약국만). 수만 건이라 오래 걸릴 수 있다
export function syncHospitals(targetScope: string) {
  return request<HospitalSyncResult>("/api/wc/admin/hospitals/sync", {
    method: "POST",
    body: JSON.stringify({ targetScope }),
  });
}

// 문의 상세(본문·답변·버전)를 조회
export async function getInquiry(id: string) {
  return mapInquiry(
    await request<unknown>(`/api/wc/admin/inquiries/${encodeURIComponent(id)}`),
  );
}

// 문의 답변을 저장 (서버는 요청 ID로 재전송을 구분하고, 문의 버전이 다르면 409로 거부)
export async function answerInquiry(
  id: string,
  expectedVersion: number,
  content: string,
  clientRequestId: string,
) {
  return mapInquiry(
    await request<unknown>(
      `/api/wc/admin/inquiries/${encodeURIComponent(id)}/answer`,
      {
        method: "PUT",
        body: JSON.stringify({ clientRequestId, expectedVersion, content }),
      },
    ),
  );
}

// 진단 처리 상태를 저장
export function updateDiagnosisStatus(id: string, status: string) {
  const statusMap: Record<string, string> = {
    진행중: "IN_PROGRESS",
    알림발송: "ALERT_SENT",
    완료: "COMPLETED",
    처리완료: "COMPLETED",
  };
  return request(`/api/wc/admin/diagnoses/${id}/status`, {
    method: "PATCH",
    body: JSON.stringify({
      status: statusMap[status] || status,
      reviewStatus: "NORMAL",
    }),
  });
}

// 사용자 계정 상태를 저장
export function updateUserStatus(id: string, active: boolean) {
  return request(`/api/wc/admin/users/${id}/status`, {
    method: "PATCH",
    body: JSON.stringify({
      status: active ? "ACTIVE" : "INACTIVE",
      reason: "관리자 화면 변경",
    }),
  });
}

// AI 재학습 이력을 조회
export function getRetrainHistory(pageNumber = 1, limit = 10) {
  return request(
    `/api/wc/admin/ai/retrain-history?page=${pageNumber}&limit=${limit}`,
  );
}

// AI 재학습을 요청
export function requestRetrain(reason: string) {
  return request("/api/wc/admin/ai/retrain", {
    method: "POST",
    body: JSON.stringify({ reason }),
  });
}

// 재학습 작업 상태를 조회
export function getRetrainJob(jobId: string) {
  return request(`/api/wc/admin/ai/jobs/${encodeURIComponent(jobId)}`);
}

// 검증된 모델 배포를 승인
export function approveRetrainJob(jobId: string) {
  return request(`/api/wc/admin/ai/jobs/${encodeURIComponent(jobId)}/approve`, {
    method: "POST",
  });
}

// 운영 분석 목록과 준비 상태를 조회
export function getOperations(status?: string, pageNumber = 0, size = 20) {
  const query = new URLSearchParams({
    page: String(pageNumber),
    size: String(size),
  });
  if (status) query.set("status", status);
  return request(`/api/wc/admin/operations?${query}`);
}

// AI 서버 상태를 조회
export function getAiStatus() {
  return request("/api/wc/admin/operations/ai-status");
}

// 서비스 준비 상태를 조회
export function getReadiness() {
  return request("/api/wc/admin/operations/readiness");
}
