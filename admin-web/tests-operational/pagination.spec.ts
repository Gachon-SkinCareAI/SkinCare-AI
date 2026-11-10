import { test, expect, type Page } from "@playwright/test";
import { createServer, type Server } from "node:http";
import { readFile } from "node:fs/promises";
import { resolve, sep, extname } from "node:path";

// 운영용 관리자 화면의 목록 하단 페이지 이동: 번호는 5개씩 묶어 보여주고, 번호 양쪽에 첫 페이지·마지막 페이지 버튼이 있다.

let server: Server;
test.beforeAll(async () => {
  const root = resolve("operational");
  server = createServer(async (request, response) => {
    try {
      const url = new URL(request.url || "/", "http://localhost");
      const file = resolve(
        root,
        "." + (url.pathname === "/" ? "/index.html" : url.pathname),
      );
      if (!file.startsWith(root + sep)) throw new Error("Invalid path");
      const data = await readFile(file);
      const type =
        { ".js": "text/javascript", ".css": "text/css", ".html": "text/html" }[
          extname(file)
        ] || "application/octet-stream";
      response.writeHead(200, { "Content-Type": type });
      response.end(data);
    } catch {
      response.writeHead(404);
      response.end();
    }
  });
  await new Promise<void>((resolve) =>
    server.listen(5182, "127.0.0.1", resolve),
  );
});
test.afterAll(async () => {
  server.closeAllConnections();
  await new Promise<void>((resolve, reject) =>
    server.close((error) => (error ? reject(error) : resolve())),
  );
});

const TOTAL = 530; // 한 페이지 20건 → 27페이지

// 서버가 해주는 페이지 처리를 흉내 낸다 (page는 0부터, size 20)
async function openHospitals(page: Page) {
  const requested: number[] = [];
  await page.route("**/api/wc/**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    if (url.pathname.endsWith("/auth/login"))
      return route.fulfill({ json: { token: "synthetic-session-token" } });
    let response: unknown;
    if (url.pathname.endsWith("/dashboard"))
      response = {
        waitingInquiries: 0,
        completedInquiries: 0,
        activeHospitals: TOTAL,
        inactiveHospitals: 0,
      };
    else if (url.pathname.endsWith("/hospitals")) {
      const number = Number(url.searchParams.get("page") ?? 0);
      requested.push(number);
      const count = Math.max(0, Math.min(20, TOTAL - number * 20));
      const items = Array.from({ length: count }, (_, i) => ({
        id: `h-${number * 20 + i}`,
        name: `기관 ${number * 20 + i + 1}`,
        type: "OTHER",
        address: "합성 주소",
        status: "ACTIVE",
        verifiedOn: "2026-11-10",
        version: 1,
      }));
      response = {
        items,
        total: TOTAL,
        page: number,
        size: 20,
        hasNext: (number + 1) * 20 < TOTAL,
      };
    } else
      response = { items: [], total: 0, page: 0, size: 20, hasNext: false };
    await route.fulfill({ json: response });
  });
  await page.goto("/");
  await page.getByLabel("관리자 아이디").fill("synthetic-admin");
  await page.getByLabel("비밀번호", { exact: true }).fill("synthetic-password");
  await page.getByRole("button", { name: "로그인", exact: true }).click();
  await expect(page.locator("#workspace")).toBeVisible();
  await page.locator('[data-tab="hospitals"]').click();
  await expect(page.locator("#hospital-list")).toContainText("기관 1");
  return requested;
}

const nav = (page: Page) => page.locator("#hospital-pages");
const numbers = async (page: Page) =>
  (
    await nav(page)
      .getByRole("button", { name: /^\d+페이지$/ })
      .allInnerTexts()
  ).map(Number);
const summary = (page: Page) => nav(page).locator("span");

test("페이지 번호는 5개씩 묶어 보여주고 번호 양쪽에 첫 페이지·마지막 페이지 버튼이 있다", async ({
  page,
}) => {
  const requested = await openHospitals(page);
  await expect(summary(page)).toHaveText("총 530건 · 1 / 27 페이지");
  expect(await numbers(page)).toEqual([1, 2, 3, 4, 5]);
  const labels = await nav(page)
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
  await expect(
    nav(page).getByRole("button", { name: "첫 페이지" }),
  ).toBeDisabled();
  await expect(
    nav(page).getByRole("button", { name: "이전 페이지" }),
  ).toBeDisabled();
  await expect(
    nav(page).getByRole("button", { name: "1페이지" }),
  ).toHaveAttribute("aria-current", "page");

  // 5페이지까지는 같은 묶음, 6페이지부터 6~10
  await nav(page).getByRole("button", { name: "5페이지" }).click();
  await expect(summary(page)).toHaveText("총 530건 · 5 / 27 페이지");
  expect(await numbers(page)).toEqual([1, 2, 3, 4, 5]);
  await nav(page).getByRole("button", { name: "다음 페이지" }).click();
  await expect(summary(page)).toHaveText("총 530건 · 6 / 27 페이지");
  expect(await numbers(page)).toEqual([6, 7, 8, 9, 10]);
  await expect(
    nav(page).getByRole("button", { name: "6페이지" }),
  ).toHaveAttribute("aria-current", "page");
  // 갈 수 없는 버튼은 목록을 불러온 뒤에도 막혀 있고, 갈 수 있는 버튼은 열려 있다
  await expect(
    nav(page).getByRole("button", { name: "첫 페이지" }),
  ).toBeEnabled();
  await expect(
    nav(page).getByRole("button", { name: "이전 페이지" }),
  ).toBeEnabled();
  await expect(page.locator("#progress")).toBeHidden();
  expect(requested.slice(-2)).toEqual([4, 5]); // 서버는 0부터

  // 마지막 페이지로: 마지막 묶음은 남은 페이지(26, 27)만
  await nav(page).getByRole("button", { name: "마지막 페이지" }).click();
  await expect(summary(page)).toHaveText("총 530건 · 27 / 27 페이지");
  expect(await numbers(page)).toEqual([26, 27]);
  expect(requested.at(-1)).toBe(26);
  await expect(page.locator("#hospital-list")).toContainText("기관 521"); // 마지막 페이지 첫 항목
  await expect(
    nav(page).getByRole("button", { name: "다음 페이지" }),
  ).toBeDisabled();
  await expect(
    nav(page).getByRole("button", { name: "마지막 페이지" }),
  ).toBeDisabled();
  await expect(page.locator("#progress")).toBeHidden();
  await expect(
    nav(page).getByRole("button", { name: "다음 페이지" }),
  ).toBeDisabled(); // 불러오는 동안 잠겼다 풀려도 계속 막혀 있다

  // 첫 페이지로
  await nav(page).getByRole("button", { name: "첫 페이지" }).click();
  await expect(summary(page)).toHaveText("총 530건 · 1 / 27 페이지");
  expect(await numbers(page)).toEqual([1, 2, 3, 4, 5]);
  expect(requested.at(-1)).toBe(0);
});

test("번호를 직접 눌러 이동하고 현재 페이지 번호는 다시 누를 수 없다", async ({
  page,
}) => {
  const requested = await openHospitals(page);
  await nav(page).getByRole("button", { name: "3페이지" }).click();
  await expect(summary(page)).toHaveText("총 530건 · 3 / 27 페이지");
  await expect(page.locator("#hospital-list")).toContainText("기관 41");
  expect(requested.at(-1)).toBe(2);
  await expect(
    nav(page).getByRole("button", { name: "3페이지" }),
  ).toBeDisabled();
});
