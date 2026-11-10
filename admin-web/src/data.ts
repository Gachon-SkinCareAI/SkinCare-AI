export const risks = ["응급", "경고", "관찰"] as const;
export type Risk = (typeof risks)[number];

export type Diagnosis = {
  id: string;
  userId: string;
  time: string;
  condition: string;
  area: string;
  risk: Risk;
  confidence: number;
  status: string;
  connection: string;
};

export type User = {
  id: string;
  name: string;
  joinedAt: string;
  diagnoses: number;
  risk: Risk;
  plan: "Premium" | "Free";
  active: boolean;
};

export type Facility = {
  id: string;
  name: string;
  type: string;
  region: string;
  night: boolean;
  emergency: boolean;
  active: boolean;
  address: string;
  phone: string;
  version?: number;
  // 동기화로 들어온 기관의 출처와 확인일 (관리자가 정보를 검증할 때 보는 값)
  source?: string;
  verifiedOn?: string;
};

export type Inquiry = {
  id: string;
  category: string;
  userId: string;
  author: string;
  subject: string;
  content: string;
  diagnosisId: string | null;
  date: string;
  reply: string;
  // 답변 등록 시 낙관적 잠금에 사용하는 서버의 문의 버전
  version?: number;
};

export type DashboardMetrics = Record<string, number>;
// AI 모델 성능 지표 한 항목 (metrics_summary의 값)
export type AiMetric = {
  value: number | null;
  unit?: string | null;
  change?: string | null;
  description?: string | null;
};
// AI 파이프라인 엔진 상태 한 항목 (pipeline_status의 값)
export type AiPipeline = {
  id?: number;
  engineName: string;
  status: string;
  avgLatency?: string | null;
  resourceUsageRate?: number | null;
};
// AI 서버 상태 (ai_health). 연결 실패 시 status가 unreachable
export type AiHealth = {
  status?: string;
  models?: Record<string, string>;
  bankVersion?: string | null;
  cardVersion?: string | null;
  inflight?: number | null;
  maxInflight?: number | null;
};
export type MonitoringData = {
  metrics: Record<string, AiMetric>;
  pipelines: AiPipeline[];
  health: AiHealth;
};
export type Store = {
  version: 3;
  dashboard: DashboardMetrics;
  users: User[];
  diagnoses: Diagnosis[];
  // 의료기관은 수만 건이라 store에 담지 않고 화면이 서버에서 페이지 단위로 조회한다 (adminApi.searchFacilities)
  inquiries: Inquiry[];
  monitoring: MonitoringData;
};
export type Detail =
  | { type: "diagnosis" | "user" | "inquiry"; id: string }
  // 의료기관은 목록에서 이미 불러온 값을 그대로 전달한다 (전체 목록이 store에 없다)
  | { type: "facility"; id: string; facility: Facility }
  | { type: "sync" };
export type Page =
  | "dashboard"
  | "diagnoses"
  | "monitoring"
  | "facilities"
  | "users"
  | "inquiries";
export type Navigate = (page: Page, risk?: Risk) => void;
export type OpenDetail = (detail: Detail) => void;

// 진단 검색과 위험도 필터를 적용
export function filterDiagnoses(
  rows: Diagnosis[],
  query: string,
  risk: string,
) {
  const term = query.trim().toLocaleLowerCase();
  return rows.filter(
    (row) =>
      (!term ||
        `${row.id} ${row.userId} ${row.condition}`
          .toLocaleLowerCase()
          .includes(term)) &&
      (risk === "전체" || row.risk === risk),
  );
}

// 문의 상태와 검색어 필터를 적용
export function filterInquiries(rows: Inquiry[], filter: string, query = "") {
  const term = query.trim().toLocaleLowerCase();
  return rows.filter((row) => {
    const answered = Boolean(row.reply.trim());
    const matchesFilter =
      filter === "전체" ||
      (filter === "답변 대기" && !answered) ||
      (filter === "답변 완료" && answered) ||
      row.category === filter;
    return (
      matchesFilter &&
      (!term ||
        `${row.id} ${row.subject} ${row.author}`
          .toLocaleLowerCase()
          .includes(term))
    );
  });
}

// CSV 파일을 생성
export function csv(rows: (string | number)[][]) {
  return `\uFEFF${rows
    .map((row) =>
      row
        .map((cell) => {
          const value = String(cell);
          const safe = /^[=+\-@]/.test(value) ? `'${value}` : value;
          return `"${safe.replaceAll('"', '""')}"`;
        })
        .join(","),
    )
    .join("\r\n")}`;
}

// CSV 파일을 다운로드
export function downloadCsv(filename: string, rows: (string | number)[][]) {
  const blob = new Blob([csv(rows)], { type: "text/csv;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  anchor.click();
  URL.revokeObjectURL(url);
}

// API 응답 저장소를 검증
export function parseStore(raw: string | null): Store {
  if (!raw) throw new Error("API 저장소가 없습니다");
  const parsed = JSON.parse(raw) as Store;
  if (
    !parsed ||
    !Array.isArray(parsed.users) ||
    !Array.isArray(parsed.diagnoses)
  )
    throw new Error("API 저장소 형식이 올바르지 않습니다");
  return parsed;
}

// 문의 답변을 화면 상태에 반영
export function saveReply(store: Store, id: string, reply: string): Store {
  const value = reply.trim();
  if (!value) throw new Error("답변을 입력해 주세요");
  if (value.length > 2000)
    throw new Error("답변은 2,000자 이내로 입력해 주세요");
  if (!store.inquiries.some((inquiry) => inquiry.id === id))
    throw new Error("문의를 찾을 수 없습니다");
  return {
    ...store,
    inquiries: store.inquiries.map((inquiry) =>
      inquiry.id === id ? { ...inquiry, reply: value } : inquiry,
    ),
  };
}

// 로컬 날짜를 YYYY-MM-DD 문자열로 변환 (진단 시각 앞부분과 비교하는 용도)
export function dayKey(date: Date) {
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}
