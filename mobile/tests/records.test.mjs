import { readFile } from "node:fs/promises";
import { test } from "node:test";
import assert from "node:assert/strict";
const code = await readFile(
  new URL("../src/services/records.js", import.meta.url),
  "utf8",
);
const { filterRecords, statusLabel } = await import(
  `data:text/javascript;base64,${Buffer.from(code).toString("base64")}`
);
test("development record filters use server processing states without claiming wound recovery", () => {
  const records = [
    "CREATED",
    "QUESTIONNAIRE",
    "COMPLETED",
    "RETRY_REQUIRED",
  ].map((status, id) => ({ id, status }));
  assert.deepEqual(
    filterRecords(records, "문진 진행 중").map((r) => r.id),
    [0, 1, 3],
  );
  assert.deepEqual(
    filterRecords(records, "분석 완료").map((r) => r.id),
    [2],
  );
  assert.equal(filterRecords(records, "전체").length, 4);
  assert.equal(statusLabel(records[2]), "분석 완료");
  assert.equal(
    statusLabel({ status: "UNKNOWN_FUTURE_STATE" }),
    "상태 확인 필요",
  );
});
