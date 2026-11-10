import { expect, test } from "@playwright/test";
import { mockAdminApi } from "./support/mockAdminApi";

const pages = [
  "대시보드",
  "진단 로그",
  "AI 모니터링",
  "의료기관 관리",
  "사용자 관리",
  "문의 관리",
];

// 모든 테스트는 상태를 유지하는 가짜 관리자 API(tests/support/mockAdminApi.ts) 위에서 실행한다.
const card = (page: import("@playwright/test").Page, label: string) =>
  page.locator(".metric-card").filter({ hasText: label });

test("대시보드와 6개 메뉴가 백엔드 집계로 동작한다", async ({ page }) => {
  await mockAdminApi(page);
  const errors: string[] = [];
  page.on("pageerror", (e) => errors.push(e.message));
  await page.goto("/admin/dashboard");
  await expect(
    page.getByRole("heading", { name: "대시보드", exact: true }),
  ).toBeVisible();
  await expect(card(page, "총 사용자")).toContainText("7");
  // 7건 중 1건은 어제 진단이므로 오늘 진단은 6건
  await expect(card(page, "오늘 진단 수")).toContainText("6");
  await expect(card(page, "응급 케이스")).toContainText("4");
  await expect(card(page, "답변 대기 문의")).toContainText("1");
  await expect(
    page.getByRole("img", { name: /최근 7일 진단 건수/ }),
  ).toBeVisible();
  await expect(page.locator(".risk-distribution")).toContainText(
    "응급 (Emergency)",
  );
  await expect(page.locator(".emergency-row")).toHaveCount(3);
  for (const title of pages) {
    await page
      .getByRole("navigation", { name: "주 메뉴" })
      .getByRole("link", { name: title, exact: true })
      .click();
    await expect(
      page.getByRole("heading", { name: title, exact: true }),
    ).toBeVisible();
    await expect(
      page.getByRole("link", { name: title, exact: true }),
    ).toHaveAttribute("aria-current", "page");
  }
  expect(errors).toEqual([]);
});

test("대시보드 응급 전체보기에서 응급 필터로 이동한다", async ({ page }) => {
  await mockAdminApi(page);
  await page.goto("/admin/dashboard");
  await page.getByRole("button", { name: "전체보기" }).click();
  await expect(
    page.getByRole("heading", { name: "진단 로그", exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: "응급", exact: true }),
  ).toHaveAttribute("aria-pressed", "true");
  const rows = page.getByRole("table").getByRole("row");
  // 헤더 1행 + 응급 4건
  await expect(rows).toHaveCount(5);
  for (const row of (await rows.all()).slice(1))
    await expect(row.getByText("응급", { exact: true })).toBeVisible();
});

test("진단 검색과 상태 변경을 서버에 저장하고 새로고침해도 유지한다", async ({
  page,
}) => {
  const api = await mockAdminApi(page);
  await page.goto("/admin/diagnoses");
  await page.getByLabel("진단 검색", { exact: true }).fill("dx-0001");
  await expect(page.getByRole("table").getByRole("row")).toHaveCount(2);
  await page
    .getByRole("button", { name: "DX-0001 진단 상세", exact: true })
    .click();
  const dialog = page.getByRole("dialog");
  await expect(dialog.getByText("3도 화상", { exact: true })).toBeVisible();
  await dialog.getByLabel("처리 상태", { exact: true }).selectOption("진행중");
  await expect
    .poll(() => api.statusRequests.map((r) => r.path))
    .toEqual(["/diagnoses/DX-0001/status"]);
  expect(api.statusRequests[0].body).toMatchObject({ status: "IN_PROGRESS" });
  await page.keyboard.press("Escape");
  await page.reload();
  await page.getByLabel("진단 검색", { exact: true }).fill("0001");
  await expect(
    page.getByRole("cell", { name: "진행중", exact: true }),
  ).toBeVisible();
});

test("진단 상세는 불러온 사용자 목록에 없는 사용자도 오류 없이 연다", async ({
  page,
}) => {
  const api = await mockAdminApi(page);
  api.diagnoses[0].userId = "USR-없음";
  const errors: string[] = [];
  page.on("pageerror", (e) => errors.push(e.message));
  await page.goto("/admin/diagnoses");
  await page
    .getByRole("button", { name: "DX-0001 진단 상세", exact: true })
    .click();
  await expect(page.getByRole("dialog")).toContainText("USR-없음");
  expect(errors).toEqual([]);
});

test("사용자 지표 카드는 서버 통계만 보여주고 임의 수치를 넣지 않는다", async ({
  page,
}) => {
  await mockAdminApi(page);
  await page.goto("/admin/users");
  await expect(card(page, "총 사용자")).toContainText("7");
  await expect(card(page, "Premium 사용자")).toContainText("2");
  await expect(card(page, "Premium 사용자")).toContainText("전환율 28.6%");
  await expect(card(page, "DAU")).toContainText("4");
  await expect(card(page, "WAU")).toContainText("6");
  // 이전에 하드코딩되어 있던 증감률·목표·재방문율 문구가 없어야 한다
  const text = await page.locator("main").innerText();
  for (const fake of [
    "+3.1%",
    "+5.4%",
    "+2.1%",
    "18.2%",
    "목표: 70%",
    "재방문율",
  ])
    expect(text).not.toContain(fake);
  await expect(page.locator(".metric-change")).toHaveCount(0);
});

test("사용자 상세의 계정 상태와 상태 필터가 동작한다", async ({ page }) => {
  const api = await mockAdminApi(page);
  await page.goto("/admin/users");
  await page.getByLabel("사용자 검색", { exact: true }).fill("김민서");
  await page
    .getByRole("button", { name: "USR-01 사용자 상세", exact: true })
    .click();
  await page
    .getByRole("button", { name: "비활성으로 변경", exact: true })
    .click();
  await expect(
    page.getByRole("dialog").getByText("비활성", { exact: true }),
  ).toBeVisible();
  expect(api.statusRequests.at(-1)).toMatchObject({
    path: "/users/USR-01/status",
    body: { status: "INACTIVE" },
  });
  await page.keyboard.press("Escape");
  await page.reload();
  await page.getByLabel("사용자 상태").selectOption("비활성");
  await page.getByLabel("사용자 검색", { exact: true }).fill("USR-01");
  await expect(page.getByRole("table").getByRole("row")).toHaveCount(2);
  await expect(
    page.getByRole("cell", { name: "비활성", exact: true }),
  ).toBeVisible();
});

test("의료기관 검색, 종류 필터와 상세 및 동기화 안내를 제공한다", async ({
  page,
}) => {
  await mockAdminApi(page);
  await page.goto("/admin/facilities");
  // 백엔드 종류 코드(PHARMACY 등)가 화면 문구(약국)로 변환되어 필터가 동작해야 한다
  await page.getByLabel("의료기관 종류").selectOption("약국");
  const rows = page.getByRole("table").getByRole("row");
  await expect(rows).toHaveCount(4);
  await page.getByLabel("의료기관 검색", { exact: true }).fill("새벽");
  await expect(rows).toHaveCount(2);
  await expect(
    page.getByRole("cell", { name: "약국", exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: "새벽공공약국 상세 보기" }).click();
  await expect(page.getByRole("dialog")).toContainText(
    "백엔드 의료기관 데이터",
  );
  await page.keyboard.press("Escape");
  await page.getByRole("button", { name: "공공데이터 동기화" }).click();
  await expect(
    page.getByRole("dialog", { name: "공공데이터 동기화" }),
  ).toBeVisible();
});

test("공공데이터 동기화를 실행하면 진행 상태와 결과를 보여주고 목록을 갱신한다", async ({
  page,
}) => {
  const api = await mockAdminApi(page);
  api.syncDelayMs = 600;
  await page.goto("/admin/facilities");
  await expect(card(page, "등록 의료기관")).toContainText("5");
  await page.getByRole("button", { name: "공공데이터 동기화" }).click();
  const dialog = page.getByRole("dialog", { name: "공공데이터 동기화" });
  await dialog.getByLabel("동기화 대상").selectOption("PHARMACY");
  await dialog.getByRole("button", { name: "동기화 시작" }).click();
  // 실행 중에는 중복 실행·대상 변경·닫기가 막힌다
  await expect(
    dialog.getByRole("button", { name: "동기화 중…" }),
  ).toBeDisabled();
  await expect(dialog.getByLabel("동기화 대상")).toBeDisabled();
  await expect(
    dialog.locator(".modal-actions").getByRole("button", { name: "닫기" }),
  ).toBeDisabled();
  await page.keyboard.press("Escape");
  await expect(dialog).toBeVisible();
  const result = dialog.getByLabel("동기화 결과");
  await expect(result).toContainText("신규 추가2건");
  await expect(result).toContainText("정보 갱신1건");
  await expect(result).toContainText("운영 중지0건");
  await expect(result).toContainText("SYNC-20261110-120000");
  expect(api.syncRequests).toEqual([{ targetScope: "PHARMACY" }]);
  // 결과를 본 뒤에도 모달은 열려 있고, 뒤의 목록과 집계는 새 데이터로 갱신된다
  await expect(
    dialog.getByRole("button", { name: "다시 동기화" }),
  ).toBeEnabled();
  await dialog
    .locator(".modal-actions")
    .getByRole("button", { name: "닫기" })
    .click();
  await expect(card(page, "등록 의료기관")).toContainText("7");
  await page.getByLabel("의료기관 검색", { exact: true }).fill("동기화약국");
  await expect(page.getByRole("table").getByRole("row")).toHaveCount(3);
});

test("동기화가 이미 진행 중이면 서버 사유를 보여주고 다시 시도할 수 있다", async ({
  page,
}) => {
  const api = await mockAdminApi(page);
  api.syncMode = "conflict";
  await page.goto("/admin/facilities");
  await page.getByRole("button", { name: "공공데이터 동기화" }).click();
  const dialog = page.getByRole("dialog", { name: "공공데이터 동기화" });
  await dialog.getByRole("button", { name: "동기화 시작" }).click();
  await expect(dialog.getByRole("alert")).toContainText(
    "동기화가 이미 진행 중입니다",
  );
  api.syncMode = "ok";
  await dialog.getByRole("button", { name: "동기화 시작" }).click();
  await expect(dialog.getByRole("alert")).toHaveCount(0);
  await expect(dialog.getByLabel("동기화 결과")).toContainText("신규 추가2건");
});

test("동기화가 서버에서 실패하면 구체적인 실패 사유를 보여준다", async ({
  page,
}) => {
  const api = await mockAdminApi(page);
  api.syncMode = "fail";
  await page.goto("/admin/facilities");
  await page.getByRole("button", { name: "공공데이터 동기화" }).click();
  const dialog = page.getByRole("dialog", { name: "공공데이터 동기화" });
  await dialog.getByRole("button", { name: "동기화 시작" }).click();
  await expect(dialog.getByRole("alert")).toContainText(
    "공공데이터 파일에서 기관 정보를 읽지 못했습니다",
  );
  await expect(dialog.getByLabel("동기화 결과")).toHaveCount(0);
  await expect(card(page, "등록 의료기관")).toContainText("5");
});

test("문의 답변은 검증 후 서버에 한 번만 저장되고 수정할 수 없다", async ({
  page,
}) => {
  const api = await mockAdminApi(page);
  await page.goto("/admin/inquiries");
  await page.getByRole("button", { name: "답변 대기", exact: true }).click();
  await page.getByRole("button", { name: "문의 INQ-001 상세보기" }).click();
  const dialog = page.getByRole("dialog");
  // 모달을 열면 상세(본문)를 서버에서 불러온다
  await expect(dialog).toContainText("결과 설명을 부탁드립니다.");
  await dialog.getByRole("button", { name: "답변 저장", exact: true }).click();
  await expect(dialog.getByRole("alert")).toHaveText("답변을 입력해 주세요.");
  expect(api.answerRequests).toHaveLength(0);
  await dialog
    .getByLabel("관리자 답변")
    .fill("테스트 답변입니다. 실제 메시지는 발송하지 않습니다.");
  await dialog.getByRole("button", { name: "답변 저장", exact: true }).click();
  // 서버가 요구하는 요청 형식: 요청 ID(UUID), 문의 버전, 답변 내용
  await expect.poll(() => api.answerRequests.length).toBe(1);
  expect(api.answerRequests[0]).toEqual({
    clientRequestId: expect.stringMatching(/^[0-9a-f-]{36}$/),
    expectedVersion: 0,
    content: "테스트 답변입니다. 실제 메시지는 발송하지 않습니다.",
  });
  await expect(dialog.getByText("답변 완료", { exact: true })).toBeVisible();
  // 등록된 답변은 읽기 전용이다 (서버가 수정을 거부)
  await expect(dialog.getByLabel("관리자 답변")).toHaveCount(0);
  await expect(dialog).toContainText("등록한 답변은 수정할 수 없습니다.");
  await page.keyboard.press("Escape");
  await expect(
    page.getByRole("button", { name: "문의 INQ-001 상세보기" }),
  ).toHaveCount(0);
  await page.reload();
  await page.getByRole("button", { name: "답변 완료", exact: true }).click();
  await page.getByRole("button", { name: "문의 INQ-001 상세보기" }).click();
  await expect(page.getByRole("dialog")).toContainText(
    "테스트 답변입니다. 실제 메시지는 발송하지 않습니다.",
  );
  await page.getByRole("button", { name: /관련 분석 기록/ }).click();
  await expect(page.getByRole("dialog", { name: "진단 상세" })).toBeVisible();
});

test("이미 답변된 문의는 목록 문구 대신 서버의 실제 답변을 보여준다", async ({
  page,
}) => {
  await mockAdminApi(page);
  await page.goto("/admin/inquiries");
  await page.getByRole("button", { name: "문의 INQ-002 상세보기" }).click();
  const dialog = page.getByRole("dialog");
  await expect(dialog).toContainText("안내해 드렸습니다.");
  await expect(dialog).not.toContainText("답변이 등록된 문의입니다.");
  await expect(dialog.getByLabel("관리자 답변")).toHaveCount(0);
});

test("문의 답변 저장이 서버에서 거부되면 오류를 보여주고 모달을 유지한다", async ({
  page,
}) => {
  const api = await mockAdminApi(page);
  await page.goto("/admin/inquiries");
  await page.getByRole("button", { name: "문의 INQ-001 상세보기" }).click();
  const dialog = page.getByRole("dialog");
  await expect(dialog).toContainText("결과 설명을 부탁드립니다.");
  // 다른 관리자가 먼저 버전을 올린 상황
  api.inquiries[0].version = 5;
  await dialog.getByLabel("관리자 답변").fill("늦은 답변");
  await dialog.getByRole("button", { name: "답변 저장", exact: true }).click();
  await expect(dialog.getByRole("alert")).toContainText(
    "문의 상태가 바뀌었습니다",
  );
  await expect(dialog.getByLabel("관리자 답변")).toHaveValue("늦은 답변");
});

test("AI 성능 카드와 파이프라인, 서버 상태를 API 응답대로 표시한다", async ({
  page,
}) => {
  await mockAdminApi(page);
  await page.goto("/admin/monitoring");
  await expect(page.locator(".metric-card")).toHaveCount(2);
  await expect(page.getByText("86.2%", { exact: true })).toBeVisible();
  await expect(page.getByText("96.5%", { exact: true })).toBeVisible();
  await expect(page.getByText("+0.8%", { exact: true })).toBeVisible();
  await expect(page.locator(".pipeline-row")).toHaveCount(2);
  await expect(page.locator(".pipeline-row").first()).toContainText(
    "YOLOv8_Segmentation",
  );
  await expect(page.locator(".pipeline-row").first()).toContainText("42%");
  await expect(page.locator(".pipeline-row .green-dot")).toHaveCount(1);
  await expect(page.locator(".pipeline-row .red-dot")).toHaveCount(1);
  await expect(page.getByText("정상", { exact: true })).toBeVisible();
  await expect(page.getByText("detect v1", { exact: false })).toBeVisible();
});

test("AI 서버에 연결할 수 없으면 임의 수치 없이 빈 상태를 안내한다", async ({
  page,
}) => {
  await mockAdminApi(page, { aiUp: false });
  await page.goto("/admin/monitoring");
  await expect(page.getByText("연결 불가", { exact: true })).toBeVisible();
  await expect(page.getByText("표시할 AI 성능 지표가 없습니다")).toBeVisible();
  await expect(
    page.getByText("등록된 파이프라인 상태가 없습니다"),
  ).toBeVisible();
  await expect(page.locator(".metric-card")).toHaveCount(0);
  await expect(page.locator(".pipeline-row")).toHaveCount(0);
});

test("전체 검색에서 기관 상세를 열고 알림에서 문의를 확인한다", async ({
  page,
}) => {
  await mockAdminApi(page);
  await page.goto("/admin/dashboard");
  await page.getByRole("button", { name: "전체 검색 열기" }).click();
  await page.getByLabel("전체 검색어").fill("우리동");
  await page
    .getByRole("dialog")
    .getByRole("button", { name: /우리동 종합병원/ })
    .click();
  await expect(
    page.getByRole("dialog", { name: "의료기관 상세" }),
  ).toBeVisible();
  await page.keyboard.press("Escape");
  await page.getByRole("button", { name: "알림 보기" }).click();
  await page.locator(".notification-item").first().click();
  await expect(
    page.getByRole("dialog", { name: "문의 INQ-001 상세" }),
  ).toBeVisible();
});

test("현재 검색 조건의 진단 CSV를 다운로드한다", async ({ page }) => {
  await mockAdminApi(page);
  await page.goto("/admin/diagnoses");
  await page.getByLabel("진단 검색", { exact: true }).fill("0001");
  const pending = page.waitForEvent("download");
  await page
    .getByRole("button", { name: "현재 진단 목록 CSV 다운로드" })
    .click();
  const download = await pending;
  expect(download.suggestedFilename()).toBe("diagnoses.csv");
  const stream = await download.createReadStream();
  const chunks: Buffer[] = [];
  for await (const chunk of stream!) chunks.push(Buffer.from(chunk));
  const text = Buffer.concat(chunks).toString("utf8");
  expect(text).toContain("DX-0001");
  expect(text).not.toContain("DX-0002");
  expect(text.split("\r\n")).toHaveLength(2);
});

test("페이지 이동과 빈 검색 결과를 처리한다", async ({ page }) => {
  await mockAdminApi(page);
  await page.goto("/admin/users");
  // 사용자 7명, 페이지당 5명
  await page.getByRole("button", { name: "다음 페이지" }).click();
  await expect(
    page.getByRole("button", { name: "USR-06 사용자 상세" }),
  ).toBeVisible();
  await page
    .getByLabel("사용자 검색", { exact: true })
    .fill("존재하지않는사용자");
  await expect(
    page.getByText("검색 결과가 없습니다", { exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: "다음 페이지" }),
  ).toBeDisabled();
  await page.getByRole("button", { name: "사용자 검색 지우기" }).click();
  await expect(
    page.getByRole("button", { name: "USR-01 사용자 상세" }),
  ).toBeVisible();
});

test("모바일 6개 화면에서 메뉴와 가로 스크롤 범위가 정상이다", async ({
  page,
}) => {
  await mockAdminApi(page);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/admin/dashboard");
  for (const title of pages) {
    await page.getByRole("button", { name: "메뉴 열기" }).click();
    await page
      .getByRole("navigation", { name: "주 메뉴" })
      .getByRole("link", { name: title, exact: true })
      .click();
    await expect(
      page.getByRole("heading", { name: title, exact: true }),
    ).toBeVisible();
    await expect
      .poll(() =>
        page
          .locator("main")
          .evaluate((e) => Math.round(e.getBoundingClientRect().width)),
      )
      .toBe(390);
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBe(true);
  }
});

// ---- 의료기관 목록: 동기화로 수만 건이 쌓여도 서버가 검색·필터·페이지를 처리한다 ----
// 회귀 방지: 화면이 첫 50건만 불러와 그 안에서만 검색·필터를 해서 "약국 50건만 보이는" 문제가 있었다.

const allKinds = ["피부과", "종합병원", "병원/의원", "약국"];

test("의료기관은 전체 건수를 보여주고 첫 페이지에 여러 종류가 섞여 나온다", async ({
  page,
}) => {
  const api = await mockAdminApi(page, { bulkFacilities: 120 });
  await page.goto("/admin/facilities");
  const rows = page.getByRole("table").getByRole("row");
  await expect(rows).toHaveCount(21); // 헤더 + 20건: 한 페이지 20건
  // 전체 건수는 서버가 알려준다 (기본 5건 + 대량 120건)
  await expect(page.locator(".pagination")).toContainText("전체 125개 기관");
  await expect(page.locator(".pagination")).toContainText("1 / 7 페이지");
  // 상단 카드는 불러온 목록이 아니라 서버의 전체 집계
  const active = api.facilities.filter((f) => f.status === "ACTIVE");
  await expect(card(page, "등록 의료기관")).toContainText(
    String(active.length),
  );
  await expect(card(page, "야간진료 가능")).toContainText(
    String(active.filter((f) => f.nightCare).length),
  );
  await expect(card(page, "등록된 약국")).toContainText(
    String(active.filter((f) => f.type === "PHARMACY").length),
  );
  // 첫 페이지가 한 종류로 쏠리지 않는다
  const shown = await page.locator("tbody tr td:nth-child(2)").allInnerTexts();
  expect(new Set(shown)).toEqual(new Set(allKinds));
});

test("이름 검색은 첫 50건 밖의 기관도 서버에서 찾고 입력마다 요청하지 않는다", async ({
  page,
}) => {
  const api = await mockAdminApi(page, { bulkFacilities: 120 });
  await page.goto("/admin/facilities");
  await expect(page.getByRole("table").getByRole("row")).toHaveCount(21);
  api.facilityQueries.length = 0;
  await page
    .getByLabel("의료기관 검색", { exact: true })
    .pressSequentially("대량기관119", { delay: 20 });
  const rows = page.getByRole("table").getByRole("row");
  await expect(rows).toHaveCount(2);
  await expect(
    page.getByRole("button", { name: "대량기관119 상세 보기" }),
  ).toBeVisible();
  await expect(page.locator(".pagination")).toContainText("전체 1개 기관");
  const searches = api.facilityQueries.filter((q) => q.includes("q="));
  expect(searches).toHaveLength(1); // 타이핑하는 동안은 기다렸다가 한 번만 요청
  expect(decodeURIComponent(searches[0])).toContain("q=대량기관119");
  expect(searches[0]).toContain("page=0");
  // 지역으로도 찾는다
  await page.getByLabel("의료기관 검색", { exact: true }).fill("해운대");
  await expect(page.locator(".pagination")).toContainText("전체 60개 기관");
});

test("종류와 운영 상태 필터는 서버로 전달되고 결과가 전체에서 걸러진다", async ({
  page,
}) => {
  const api = await mockAdminApi(page, { bulkFacilities: 120 });
  await page.goto("/admin/facilities");
  await expect(page.getByRole("table").getByRole("row")).toHaveCount(21);

  await page.getByLabel("의료기관 종류").selectOption("피부과");
  const derm = api.facilities.filter((f) => f.type === "DERMATOLOGY").length;
  await expect(page.locator(".pagination")).toContainText(
    `전체 ${derm}개 기관`,
  );
  expect(api.facilityQueries.at(-1)).toContain("type=DERMATOLOGY");
  const onlyDerm = new Set(
    await page.locator("tbody tr td:nth-child(2)").allInnerTexts(),
  );
  expect(onlyDerm).toEqual(new Set(["피부과"]));

  // 종류를 약국으로 바꾸고 운영 중지·숨김만 본다
  await page.getByLabel("의료기관 종류").selectOption("약국");
  await page.getByLabel("운영 상태").selectOption("운영 중지·숨김");
  const stopped = api.facilities.filter(
    (f) => f.type === "PHARMACY" && f.status === "INACTIVE",
  ).length;
  await expect(page.locator(".pagination")).toContainText(
    `전체 ${stopped}개 기관`,
  );
  expect(api.facilityQueries.at(-1)).toContain("status=INACTIVE");
  expect(api.facilityQueries.at(-1)).toContain("type=PHARMACY");
  await expect(
    page.getByRole("cell", { name: "중지·숨김" }).first(),
  ).toBeVisible();

  // 필터를 모두 풀면 전체로 돌아온다
  await page.getByLabel("의료기관 종류").selectOption("모든 종류");
  await page.getByLabel("운영 상태").selectOption("모든 상태");
  await expect(page.locator(".pagination")).toContainText("전체 125개 기관");
});

test("페이지 이동은 서버의 해당 페이지를 불러온다", async ({ page }) => {
  const api = await mockAdminApi(page, { bulkFacilities: 120 });
  await page.goto("/admin/facilities");
  await expect(page.locator(".pagination")).toContainText("1 / 7 페이지");
  const first = await page.locator("tbody tr td:nth-child(1)").allInnerTexts();
  expect(first).toHaveLength(20);
  await page.getByRole("button", { name: "다음 페이지" }).click();
  await expect(page.locator(".pagination")).toContainText("2 / 7 페이지");
  expect(api.facilityQueries.at(-1)).toContain("page=1");
  const second = await page.locator("tbody tr td:nth-child(1)").allInnerTexts();
  expect(second).toHaveLength(20);
  expect(second.filter((name) => first.includes(name))).toEqual([]); // 겹치지 않는다
  // 마지막 페이지는 남은 5건
  await page.getByRole("button", { name: "마지막 페이지" }).click();
  await expect(page.locator(".pagination")).toContainText("7 / 7 페이지");
  await expect(page.getByRole("table").getByRole("row")).toHaveCount(6);
  await expect(
    page.getByRole("button", { name: "다음 페이지" }),
  ).toBeDisabled();
});

test("첫 페이지 밖에서 연 의료기관 상세와 전체 검색도 동작한다", async ({
  page,
}) => {
  await mockAdminApi(page, { bulkFacilities: 120 });
  await page.goto("/admin/facilities");
  await page.getByLabel("의료기관 검색", { exact: true }).fill("대량기관119");
  await page.getByRole("button", { name: "대량기관119 상세 보기" }).click();
  const dialog = page.getByRole("dialog", { name: "의료기관 상세" });
  await expect(dialog).toContainText("대량로 119번지");
  await expect(dialog).toContainText("건강보험심사평가원·국립중앙의료원");
  await expect(dialog).toContainText("2026-11-10");
  await page.keyboard.press("Escape");

  // 상단 전체 검색: 의료기관은 서버에서 찾는다
  await page.getByRole("button", { name: "전체 검색 열기" }).click();
  await page.getByLabel("전체 검색어").fill("대량기관118");
  await page
    .getByRole("dialog")
    .getByRole("button", { name: /대량기관118/ })
    .click();
  await expect(
    page.getByRole("dialog", { name: "의료기관 상세" }),
  ).toContainText("대량로 118번지");
});

test("의료기관 목록 조회가 실패하면 사유를 보여주고 다시 시도할 수 있다", async ({
  page,
}) => {
  await mockAdminApi(page, { bulkFacilities: 30 });
  let fail = true;
  await page.route(/\/api\/wc\/admin\/hospitals(\?|$)/, (route) =>
    fail
      ? route.fulfill({
          status: 503,
          json: { message: "서버가 준비 중입니다" },
        })
      : route.fallback(),
  );
  await page.goto("/admin/facilities");
  await expect(page.getByRole("alert")).toContainText("서버가 준비 중입니다");
  await expect(page.getByRole("table")).toHaveCount(0);
  fail = false;
  await page.getByRole("button", { name: "다시 시도" }).click();
  await expect(page.getByRole("table").getByRole("row")).toHaveCount(21);
  await expect(page.getByRole("alert")).toHaveCount(0);
});

test("동기화로 기관이 늘면 목록과 상단 집계가 서버 값으로 갱신된다", async ({
  page,
}) => {
  const api = await mockAdminApi(page, { bulkFacilities: 120 });
  await page.goto("/admin/facilities");
  await expect(page.locator(".pagination")).toContainText("전체 125개 기관");
  const before = api.facilities.filter((f) => f.status === "ACTIVE").length;
  await page.getByRole("button", { name: "공공데이터 동기화" }).click();
  const dialog = page.getByRole("dialog", { name: "공공데이터 동기화" });
  await dialog.getByRole("button", { name: "동기화 시작" }).click();
  await expect(dialog.getByLabel("동기화 결과")).toContainText("신규 추가2건");
  await dialog
    .locator(".modal-actions")
    .getByRole("button", { name: "닫기" })
    .click();
  await expect(page.locator(".pagination")).toContainText("전체 127개 기관");
  await expect(card(page, "등록 의료기관")).toContainText(String(before + 2));
});

// ---- 페이지 번호: 5개씩 묶어 보여주고, 번호 양쪽에 첫 페이지·마지막 페이지 이동 버튼이 있다 ----

const pageNav = (page: import("@playwright/test").Page) =>
  page.getByRole("navigation", { name: "표 페이지 이동" });
const numberButtons = (page: import("@playwright/test").Page) =>
  pageNav(page).getByRole("button", { name: /^\d+페이지$/ });
const shownNumbers = async (page: import("@playwright/test").Page) =>
  (await numberButtons(page).allInnerTexts()).map(Number);

test("페이지 번호는 5개씩 묶어 보여준다 (1~5, 6~10, 11~15 ...)", async ({
  page,
}) => {
  // 기본 5건 + 대량 520건 = 525건 → 한 페이지 20건이므로 27페이지
  await mockAdminApi(page, { bulkFacilities: 520 });
  await page.goto("/admin/facilities");
  await expect(page.locator(".pagination")).toContainText("1 / 27 페이지");
  expect(await shownNumbers(page)).toEqual([1, 2, 3, 4, 5]);

  // 5페이지까지는 같은 묶음이 유지된다
  await pageNav(page).getByRole("button", { name: "5페이지" }).click();
  await expect(page.locator(".pagination")).toContainText("5 / 27 페이지");
  expect(await shownNumbers(page)).toEqual([1, 2, 3, 4, 5]);
  await expect(
    pageNav(page).getByRole("button", { name: "5페이지" }),
  ).toHaveAttribute("aria-current", "page");

  // 6페이지부터 다음 묶음 6~10
  await page.getByRole("button", { name: "다음 페이지" }).click();
  await expect(page.locator(".pagination")).toContainText("6 / 27 페이지");
  expect(await shownNumbers(page)).toEqual([6, 7, 8, 9, 10]);
  await expect(
    pageNav(page).getByRole("button", { name: "6페이지" }),
  ).toHaveAttribute("aria-current", "page");
  // 이전 페이지로 돌아가면 이전 묶음
  await page.getByRole("button", { name: "이전 페이지" }).click();
  await expect(page.locator(".pagination")).toContainText("5 / 27 페이지");
  expect(await shownNumbers(page)).toEqual([1, 2, 3, 4, 5]);

  // 10페이지는 6~10 묶음의 끝, 11페이지에서 11~15로 넘어간다
  await page.getByRole("button", { name: "다음 페이지" }).click();
  await pageNav(page).getByRole("button", { name: "10페이지" }).click();
  expect(await shownNumbers(page)).toEqual([6, 7, 8, 9, 10]);
  await page.getByRole("button", { name: "다음 페이지" }).click();
  await expect(page.locator(".pagination")).toContainText("11 / 27 페이지");
  expect(await shownNumbers(page)).toEqual([11, 12, 13, 14, 15]);
});

test("첫 페이지와 마지막 페이지 버튼이 번호 양쪽에서 처음과 끝으로 이동한다", async ({
  page,
}) => {
  const api = await mockAdminApi(page, { bulkFacilities: 520 });
  await page.goto("/admin/facilities");
  await expect(page.locator(".pagination")).toContainText("1 / 27 페이지");
  // 첫 페이지에서는 첫 페이지·이전이 막히고 다음·마지막은 열려 있다
  await expect(page.getByRole("button", { name: "첫 페이지" })).toBeDisabled();
  await expect(
    page.getByRole("button", { name: "이전 페이지" }),
  ).toBeDisabled();
  await expect(page.getByRole("button", { name: "다음 페이지" })).toBeEnabled();
  await expect(
    page.getByRole("button", { name: "마지막 페이지" }),
  ).toBeEnabled();
  // 버튼 순서: 첫 페이지 · 이전 · 번호 · 다음 · 마지막 페이지 (번호 묶음의 왼쪽과 오른쪽 끝)
  const labels = await pageNav(page)
    .getByRole("button")
    .evaluateAll((els) => els.map((e) => e.getAttribute("aria-label")));
  expect(labels).toEqual([
    "첫 페이지",
    "이전 페이지",
    "1페이지",
    "2페이지",
    "3페이지",
    "4페이지",
    "5페이지",
    "다음 페이지",
    "마지막 페이지",
  ]);

  await page.getByRole("button", { name: "마지막 페이지" }).click();
  await expect(page.locator(".pagination")).toContainText("27 / 27 페이지");
  expect(api.facilityQueries.at(-1)).toContain("page=26"); // 서버는 0부터
  // 마지막 묶음은 남은 페이지(26, 27)만 보여준다
  expect(await shownNumbers(page)).toEqual([26, 27]);
  await expect(page.getByRole("table").getByRole("row")).toHaveCount(6); // 525건 중 마지막 5건 + 헤더
  await expect(
    page.getByRole("button", { name: "다음 페이지" }),
  ).toBeDisabled();
  await expect(
    page.getByRole("button", { name: "마지막 페이지" }),
  ).toBeDisabled();
  await expect(page.getByRole("button", { name: "첫 페이지" })).toBeEnabled();

  await page.getByRole("button", { name: "첫 페이지" }).click();
  await expect(page.locator(".pagination")).toContainText("1 / 27 페이지");
  expect(api.facilityQueries.at(-1)).toContain("page=0");
  expect(await shownNumbers(page)).toEqual([1, 2, 3, 4, 5]);
});

test("전체가 5페이지 이하면 있는 번호만 보여주고 한 페이지면 이동 버튼이 모두 막힌다", async ({
  page,
}) => {
  await mockAdminApi(page); // 기관 5건 → 1페이지
  await page.goto("/admin/facilities");
  await expect(page.locator(".pagination")).toContainText("1 / 1 페이지");
  expect(await shownNumbers(page)).toEqual([1]);
  for (const name of [
    "첫 페이지",
    "이전 페이지",
    "다음 페이지",
    "마지막 페이지",
  ])
    await expect(page.getByRole("button", { name })).toBeDisabled();
});

test("페이지 번호가 필요한 다른 화면(사용자)에서도 같은 이동 버튼을 쓴다", async ({
  page,
}) => {
  await mockAdminApi(page); // 사용자 7명 → 5명씩 2페이지
  await page.goto("/admin/users");
  await expect(page.locator(".pagination")).toContainText("1 / 2 페이지");
  await page.getByRole("button", { name: "마지막 페이지" }).click();
  await expect(page.locator(".pagination")).toContainText("2 / 2 페이지");
  await expect(
    page.getByRole("button", { name: "USR-06 사용자 상세" }),
  ).toBeVisible();
  await page.getByRole("button", { name: "첫 페이지" }).click();
  await expect(
    page.getByRole("button", { name: "USR-01 사용자 상세" }),
  ).toBeVisible();
});

test("좁은 화면에서도 페이지 이동 버튼이 화면 안에 들어온다", async ({
  page,
}) => {
  await mockAdminApi(page, { bulkFacilities: 520 });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/admin/facilities");
  await expect(page.locator(".pagination")).toContainText("1 / 27 페이지");
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  for (const name of [
    "첫 페이지",
    "이전 페이지",
    "1페이지",
    "5페이지",
    "다음 페이지",
    "마지막 페이지",
  ]) {
    const box = await pageNav(page)
      .getByRole("button", { name, exact: true })
      .boundingBox();
    expect(box, name).not.toBeNull();
    expect(box!.x, name).toBeGreaterThanOrEqual(0);
    expect(box!.x + box!.width, name).toBeLessThanOrEqual(390);
  }
});
