import type { Page, Route } from "@playwright/test";

// 실제 백엔드(관리자 API)와 같은 응답 형식을 돌려주는 상태 유지형 테스트 서버.
// 상태 변경(PATCH/PUT)이 반영되므로 새로고침 후 유지 여부도 검증할 수 있다.

const pad = (n: number) => String(n).padStart(2, "0");
const dayKey = (d: Date) =>
  `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
const now = new Date();
const today = dayKey(now);
const yesterday = dayKey(
  new Date(now.getFullYear(), now.getMonth(), now.getDate() - 1),
);

const names = [
  "김민서",
  "이서준",
  "박지우",
  "최하윤",
  "정도현",
  "한소율",
  "오세훈",
];
const diagnosisRows: [
  string,
  string,
  string,
  string,
  string,
  string,
  string,
][] = [
  ["DX-0001", "USR-01", "EMERGENCY", "3도 화상", "팔", "알림발송", today],
  ["DX-0002", "USR-02", "EMERGENCY", "2도 화상", "다리", "진행중", today],
  ["DX-0003", "USR-03", "EMERGENCY", "열상", "손", "진행중", today],
  ["DX-0004", "USR-04", "EMERGENCY", "심부 열상", "얼굴", "완료", today],
  ["DX-0005", "USR-05", "WARNING", "찰과상", "무릎", "완료", today],
  ["DX-0006", "USR-06", "WARNING", "접촉성 피부염", "목", "진행중", today],
  [
    "DX-0007",
    "USR-07",
    "OBSERVATION",
    "건조 피부",
    "팔꿈치",
    "완료",
    yesterday,
  ],
];
const facilityRows: [string, string, string, string, boolean, boolean][] = [
  ["FAC-1", "서울피부과의원", "DERMATOLOGY", "서울 강남구", false, false],
  ["FAC-2", "우리동 종합병원", "GENERAL_HOSPITAL", "서울 마포구", true, true],
  ["FAC-3", "새벽공공약국", "PHARMACY", "서울 종로구", true, false],
  ["FAC-4", "온누리약국", "PHARMACY", "경기 성남시", false, false],
  ["FAC-5", "푸른약국", "PHARMACY", "인천 남동구", false, false],
];

const statusLabels: Record<string, string> = {
  IN_PROGRESS: "진행중",
  ALERT_SENT: "알림발송",
  COMPLETED: "완료",
  PENDING: "대기",
};
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

export type MockState = ReturnType<typeof createState>;

function createState(aiUp: boolean, bulkFacilities = 0) {
  return {
    aiUp,
    users: names.map((name, i) => ({
      userId: `USR-0${i + 1}`,
      nickname: name,
      joinedAt: "2026.11.01",
      totalDiagnoses: 1,
      latestRisk: "OBSERVATION",
      // USR-02, USR-03은 Premium (통계 카드 검증용)
      plan: i === 1 || i === 2 ? "PREMIUM" : "FREE",
      status: "ACTIVE",
    })),
    diagnoses: diagnosisRows.map(
      ([
        diagnosisId,
        userId,
        riskLevel,
        suspectedDisease,
        bodyPart,
        statusLabel,
        day,
      ]) => ({
        diagnosisId,
        userId,
        createdAt: `${day} 09:00:00`,
        suspectedDisease,
        bodyPart,
        riskLevel,
        confidenceScore: 90,
        statusLabel,
      }),
    ),
    facilities: [
      ...facilityRows.map(([id, name, type, region, nightCare, emergency]) => ({
        id,
        name,
        type,
        region,
        nightCare,
        emergency,
        status: "ACTIVE",
        address: `${region} 테스트로 1`,
        tel: "02-000-0000",
        dataSource: "테스트 출처",
        verifiedOn: "2026-11-10",
        version: 1,
      })),
      // 동기화 뒤 수만 건이 쌓인 상황을 흉내 낸다: 약국·병원이 섞이고 10번째마다 운영 중지
      ...Array.from({ length: bulkFacilities }, (_, i) => ({
        id: `BULK-${String(i).padStart(4, "0")}`,
        name: `대량기관${String(i).padStart(3, "0")}`,
        type: ["PHARMACY", "OTHER", "DERMATOLOGY", "GENERAL_HOSPITAL"][i % 4],
        region: i % 2 === 0 ? "서울 강남구" : "부산 해운대구",
        nightCare: i % 3 === 0,
        emergency: i % 13 === 0,
        status: i % 10 === 0 ? "INACTIVE" : "ACTIVE",
        address: `대량로 ${i}번지`,
        tel: "02-111-2222",
        dataSource: "건강보험심사평가원·국립중앙의료원",
        verifiedOn: "2026-11-10",
        version: 0,
      })),
    ],
    // 의료기관 목록 조회 요청(쿼리 문자열)을 기록한다: 검색·필터·페이지가 서버로 전달되는지 검증용
    facilityQueries: [] as string[],
    inquiries: [
      {
        id: "INQ-001",
        type: "AI_RESULT",
        title: "분석 결과가 이상해요",
        status: "WAITING",
        analysisId: "DX-0001",
        createdAt: `${today}T09:00:00`,
        version: 0,
        content: "결과 설명을 부탁드립니다.",
        answer: null as null | { content: string; createdAt: string },
      },
      {
        id: "INQ-002",
        type: "OTHER",
        title: "앱 사용 문의",
        status: "COMPLETED",
        analysisId: null as string | null,
        createdAt: `${yesterday}T10:00:00`,
        version: 1,
        content: "사용 방법이 궁금합니다.",
        answer: {
          content: "안내해 드렸습니다.",
          createdAt: `${yesterday}T11:00:00`,
        },
      },
    ],
    // 화면이 서버로 보낸 변경 요청 (검증용)
    answerRequests: [] as Record<string, unknown>[],
    statusRequests: [] as { path: string; body: Record<string, unknown> }[],
    // 공공데이터 동기화 요청과 서버 응답 방식 (테스트가 바꿔서 성공·진행 중·실패를 재현)
    syncRequests: [] as Record<string, unknown>[],
    syncMode: "ok" as "ok" | "conflict" | "fail",
    syncDelayMs: 0,
  };
}

const list = (key: string, rows: unknown[], total = rows.length) => ({
  resultCode: 200,
  resultMsg: "ok",
  data: {},
  [key]: rows,
  total_count: total,
  total_pages: 1,
  current_page: 1,
  page_size: 50,
});
const items = (rows: unknown[]) => ({
  items: rows,
  page: 0,
  size: 50,
  hasNext: false,
  total: rows.length,
});
// 목록 응답은 본문·답변 없이, 상세 응답은 모두 포함한다 (실제 InquiryView 동작)
const summary = (q: MockState["inquiries"][number]) => ({
  ...q,
  content: null,
  answer: null,
});

export async function mockAdminApi(
  page: Page,
  options: { aiUp?: boolean; bulkFacilities?: number } = {},
) {
  const state = createState(options.aiUp ?? true, options.bulkFacilities ?? 0);
  // 로그인 화면을 거치지 않고 인증된 상태로 시작 (로그인 흐름은 login.spec.ts에서 검증)
  await page.addInitScript(() => {
    localStorage.setItem("skincare-admin-token", "test-admin-token");
    localStorage.setItem("skincare-admin-name", "테스트 관리자");
    localStorage.setItem("skincare-admin-id", "operator");
  });
  await page.route("**/api/wc/admin/**", (route) => handle(route, state));
  return state;
}

async function handle(route: Route, s: MockState) {
  const request = route.request();
  const url = new URL(request.url());
  const path = url.pathname.replace("/api/wc/admin", "");
  const method = request.method();
  const body = method === "GET" ? {} : (request.postDataJSON() ?? {});
  const json = (value: unknown, status = 200) =>
    route.fulfill({ status, json: value });

  if (method === "POST" && path === "/auth/logout")
    return json({ resultCode: 200 });
  if (method === "GET") {
    if (path === "/dashboard")
      return json({
        waitingInquiries: s.inquiries.filter((q) => q.status === "WAITING")
          .length,
        completedInquiries: s.inquiries.filter((q) => q.status === "COMPLETED")
          .length,
        // 실제 AdminDashboardController와 같은 전체 집계 (목록과 달리 페이지가 아니다)
        activeHospitals: s.facilities.filter((f) => f.status === "ACTIVE")
          .length,
        inactiveHospitals: s.facilities.filter((f) => f.status === "INACTIVE")
          .length,
        activeNightCare: s.facilities.filter(
          (f) => f.status === "ACTIVE" && f.nightCare,
        ).length,
        activeEmergency: s.facilities.filter(
          (f) => f.status === "ACTIVE" && f.emergency,
        ).length,
        activePharmacies: s.facilities.filter(
          (f) => f.status === "ACTIVE" && f.type === "PHARMACY",
        ).length,
      });
    if (path === "/users") {
      // 실제 AdminUserService.getUserStats()와 같은 구조의 통계
      const premium = s.users.filter((u) => u.plan === "PREMIUM").length;
      return json({
        ...list("user_list", s.users),
        stats: {
          total_users: s.users.length,
          premium_users: {
            count: premium,
            conversion_rate: `${Math.round((premium * 1000) / s.users.length) / 10}%`,
          },
          dau: { count: 4 },
          wau: { count: 6 },
          suspended_users: 0,
        },
      });
    }
    if (path === "/diagnoses") {
      const risk = url.searchParams.get("risk_level");
      const rows = risk
        ? s.diagnoses.filter((d) => d.riskLevel === risk)
        : s.diagnoses;
      return json(list("diagnoses_list", rows));
    }
    if (path === "/hospitals") {
      // 실제 AdminHospitalController와 같은 규칙: 이름순, 검색·유형·상태 필터, 한 페이지 최대 50건
      s.facilityQueries.push(url.search.replace(/^\?/, ""));
      const q = (url.searchParams.get("q") ?? "").toLowerCase();
      const type = url.searchParams.get("type");
      const status = url.searchParams.get("status");
      const page = Number(url.searchParams.get("page") ?? 0);
      const size = Number(url.searchParams.get("size") ?? 20);
      if (!(size >= 1 && size <= 50) || !(page >= 0))
        return json({ message: "페이지 범위를 확인해 주세요." }, 400);
      if (status && !["ACTIVE", "INACTIVE"].includes(status.toUpperCase()))
        return json(
          { message: "운영 상태는 ACTIVE 또는 INACTIVE여야 합니다." },
          400,
        );
      const rows = s.facilities
        .filter(
          (f) =>
            (!q ||
              `${f.name} ${f.region} ${f.address}`.toLowerCase().includes(q)) &&
            (!type || f.type === type) &&
            (!status || f.status === status.toUpperCase()),
        )
        .sort(
          (a, b) =>
            a.name.localeCompare(b.name, "ko") || a.id.localeCompare(b.id),
        );
      return json({
        items: rows.slice(page * size, (page + 1) * size),
        page,
        size,
        hasNext: (page + 1) * size < rows.length,
        total: rows.length,
      });
    }
    if (path === "/inquiries") return json(items(s.inquiries.map(summary)));
    const detail = path.match(/^\/inquiries\/([^/]+)$/);
    if (detail) {
      const q = s.inquiries.find(
        (row) => row.id === decodeURIComponent(detail[1]),
      );
      return q ? json(q) : json({ message: "문의를 찾을 수 없습니다." }, 404);
    }
    if (path === "/ai/metrics")
      return json({
        resultCode: 200,
        data: {},
        metrics_summary: s.aiUp
          ? {
              macro_f1: {
                value: 86.2,
                unit: "%",
                change: "+0.8%",
                description: "분류 모델 Macro F1",
              },
              burn_recall: {
                value: 96.5,
                unit: "%",
                change: null,
                description: "화상 재현율",
              },
            }
          : {},
      });
    if (path === "/ai/pipelines")
      return json({
        resultCode: 200,
        data: {},
        pipeline_status: s.aiUp
          ? [
              {
                id: 1,
                engineName: "YOLOv8_Segmentation",
                status: "HEALTHY",
                avgLatency: "120ms",
                resourceUsageRate: 42,
              },
              {
                id: 2,
                engineName: "ViT_Classifier",
                status: "UNHEALTHY",
                avgLatency: "310ms",
                resourceUsageRate: 77,
              },
            ]
          : [],
      });
    if (path === "/ai/health")
      return json({
        resultCode: 200,
        data: {},
        ai_health: s.aiUp
          ? {
              status: "ok",
              models: { detect: "v1", classify: "v2" },
              bankVersion: "b1",
              cardVersion: "c1",
              inflight: 0,
              maxInflight: 2,
            }
          : {
              status: "unreachable",
              models: {},
              bankVersion: null,
              cardVersion: null,
              inflight: null,
              maxInflight: null,
            },
      });
  }
  if (method === "POST" && path === "/hospitals/sync") {
    s.syncRequests.push(body);
    if (s.syncDelayMs)
      await new Promise((done) => setTimeout(done, s.syncDelayMs));
    if (s.syncMode === "conflict")
      return json(
        { message: "동기화가 이미 진행 중입니다. 끝난 뒤 다시 시도해 주세요." },
        409,
      );
    if (s.syncMode === "fail")
      // 실제 CustomException 응답 형태: 일반 message와 구체적 사유 details
      return json(
        {
          code: "HOSPITAL-004",
          message: "병원 정보 동기화에 실패했습니다.",
          details: "공공데이터 파일에서 기관 정보를 읽지 못했습니다.",
          status: 500,
        },
        500,
      );
    // 새로 가져온 기관 2건이 목록과 집계에 반영된다
    for (const n of [1, 2])
      s.facilities.push({
        id: `HIRA-sync-${n}`,
        name: `동기화약국${n}`,
        type: "PHARMACY",
        region: "서울 중구",
        nightCare: false,
        emergency: false,
        status: "ACTIVE",
        address: "서울 중구 동기화로 1",
        tel: "02-111-2222",
        dataSource: "국립중앙의료원 약국정보",
        verifiedOn: "2026-11-10",
        version: 0,
      });
    return json({
      syncId: "SYNC-20261110-120000",
      targetScope: String(body.targetScope ?? "ALL"),
      executedAt: "2026-11-10 12:00:00",
      durationSeconds: 1.5,
      status: "COMPLETED",
      metrics: { totalFetched: 3, newAdded: 2, updated: 1, deactivated: 0 },
    });
  }
  if (method === "PATCH") {
    const user = path.match(/^\/users\/([^/]+)\/status$/);
    if (user) {
      s.statusRequests.push({ path, body });
      const row = s.users.find((u) => u.userId === user[1]);
      if (!row)
        return json({ message: "존재하지 않는 사용자 계정입니다." }, 404);
      row.status = String(body.status);
      return json({ resultCode: 200, resultMsg: "ok" });
    }
    const diagnosis = path.match(/^\/diagnoses\/([^/]+)\/status$/);
    if (diagnosis) {
      s.statusRequests.push({ path, body });
      const row = s.diagnoses.find((d) => d.diagnosisId === diagnosis[1]);
      if (!row) return json({ message: "존재하지 않는 분석입니다." }, 404);
      row.statusLabel =
        statusLabels[String(body.status)] ?? String(body.status);
      return json({ resultCode: 200, resultMsg: "ok" });
    }
  }
  if (method === "PUT") {
    const answer = path.match(/^\/inquiries\/([^/]+)\/answer$/);
    if (answer) {
      s.answerRequests.push(body);
      const q = s.inquiries.find(
        (row) => row.id === decodeURIComponent(answer[1]),
      );
      if (!q) return json({ message: "문의를 찾을 수 없습니다." }, 404);
      // 실제 서버와 같은 검사: 요청 ID 형식, 이미 답변이 있으면 거부, 버전 불일치 거부
      if (!uuid.test(String(body.clientRequestId ?? "")))
        return json({ message: "요청 ID 형식을 확인해 주세요." }, 400);
      if (q.answer)
        return json(
          { message: "이미 답변이 등록된 문의입니다. 새로고침해 주세요." },
          409,
        );
      if (body.expectedVersion !== q.version)
        return json(
          { message: "문의 상태가 바뀌었습니다. 새로고침해 주세요." },
          409,
        );
      q.answer = {
        content: String(body.content),
        createdAt: now.toISOString(),
      };
      q.status = "COMPLETED";
      q.version += 1;
      return json(q);
    }
  }
  return json({ message: `테스트 서버에 없는 요청: ${method} ${path}` }, 404);
}
