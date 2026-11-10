import { state } from "./state.js";

export const $ = (s) => document.querySelector(s);

export function element(tag, text, className) {
  const e = document.createElement(tag);
  if (text !== undefined) e.textContent = text;
  if (className) e.className = className;
  return e;
}

export function message(text, error = false) {
  const m = $("#message");
  m.textContent = text;
  m.className = error ? "error" : "";
  m.hidden = !text;
  m.setAttribute("role", error ? "alert" : "status");
}

export function signedOut() {
  state.token = null;
  state.selectedInquiry = null;
  state.editingHospital = null;
  state.replyRequest = null;
  $("#workspace").hidden = true;
  $("#login-panel").hidden = false;
  $("#logout").hidden = true;
  for (const id of [
    "inquiry-list",
    "inquiry-detail",
    "hospital-list",
    "dashboard",
    "analysis-counts",
    "analysis-list",
    "analysis-pages",
    "ai-status",
    "review-list",
    "review-detail",
    "review-pages",
    "audit-list",
    "audit-pages",
    "review-capabilities",
    "readiness-status",
  ])
    $("#" + id).replaceChildren();
  $("#login-form").reset();
  $("#review-form").reset();
  $("#review-editor").hidden = true;
  state.reviewRequest = null;
  state.reviewCapabilities = null;
}

export function lock(value) {
  state.busy = value;
  $("#progress").hidden = !value;
  document.querySelectorAll("button,input,select,textarea").forEach((e) => {
    e.disabled = value || e.dataset.permanentDisabled === "true";
  });
}

export async function run(task) {
  if (state.busy) return;
  lock(true);
  message("");
  try {
    await task();
  } catch (e) {
    message(e.message || "연결 상태를 확인해 주세요.", true);
  } finally {
    lock(false);
  }
}

export function showTab(next) {
  state.tab = next;
  for (const name of [
    "inquiries",
    "hospitals",
    "operations",
    "reviews",
    "audit",
    "account",
  ])
    $("#" + name + "-panel").hidden = next !== name;
  document.querySelectorAll("[data-tab]").forEach((e) => {
    e.className = e.dataset.tab === next ? "" : "secondary";
    e.setAttribute("aria-current", e.dataset.tab === next ? "page" : "false");
  });
}

// 한 번에 보여주는 페이지 번호 수: 1~5, 6~10, 11~15 ... (마지막 묶음은 남은 페이지까지만)
export const PAGE_GROUP = 5;

// 현재 페이지(1부터)가 속한 묶음의 첫 번호
export function pageGroupStart(page) {
  return Math.floor((Math.max(1, page) - 1) / PAGE_GROUP) * PAGE_GROUP + 1;
}

// 목록 하단의 페이지 이동: [첫 페이지] [이전] 번호 5개 [다음] [마지막 페이지]
// result.page는 0부터, 화면 표기와 load 호출은 서버 규칙(0부터)을 따른다.
export function pages(root, result, load) {
  root.replaceChildren();
  const current = result.page + 1;
  const last = Math.max(1, Math.ceil(result.total / (result.size || 20)));
  root.append(
    element(
      "span",
      `총 ${result.total}건 · ${current} / ${last} 페이지`,
      "muted",
    ),
  );
  // lock()은 모든 버튼을 다시 켜므로, 갈 수 없는 버튼은 permanentDisabled로 표시해 둔다
  const button = (label, aria, target, disabled, primary = false) => {
    const b = element("button", label, primary ? "" : "secondary");
    b.setAttribute("aria-label", aria);
    if (disabled) {
      b.disabled = true;
      b.dataset.permanentDisabled = "true";
    } else b.onclick = () => run(() => load(target - 1));
    return b;
  };
  root.append(
    button("«", "첫 페이지", 1, current === 1),
    button("이전", "이전 페이지", current - 1, current === 1),
  );
  const start = pageGroupStart(current);
  for (let n = start; n <= Math.min(start + PAGE_GROUP - 1, last); n++) {
    const b = button(String(n), `${n}페이지`, n, n === current, n === current);
    if (n === current) b.setAttribute("aria-current", "page");
    root.append(b);
  }
  root.append(
    button("다음", "다음 페이지", current + 1, current === last),
    button("»", "마지막 페이지", last, current === last),
  );
}
