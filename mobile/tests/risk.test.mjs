import { readFile } from "node:fs/promises";
import { test } from "node:test";
import assert from "node:assert/strict";
const source = await readFile(
  new URL("../src/types/analysis.js", import.meta.url),
  "utf8",
);
const { riskLevel, recordRisk, RISK_STEPS } = await import(
  `data:text/javascript;base64,${Buffer.from(source).toString("base64")}`
);

test("three existing API risk codes retain their ordered stages", () => {
  assert.deepEqual(RISK_STEPS, ["OBSERVATION", "WARNING", "EMERGENCY"]);
  assert.deepEqual(
    RISK_STEPS.map((code) => riskLevel(code).stage),
    [1, 2, 3],
  );
  assert.deepEqual(
    RISK_STEPS.map((code) => riskLevel(code).title),
    ["관찰", "경고", "응급"],
  );
  assert.equal(
    new Set(RISK_STEPS.map((code) => riskLevel(code).color)).size,
    3,
  );
});
test("missing, unsupported and image-class values never imply an observation result", () => {
  for (const code of [
    undefined,
    null,
    "",
    "UNKNOWN",
    "normal_skin",
    "toString",
    "__proto__",
    "NEW_CODE",
  ]) {
    const value = riskLevel(code);
    assert.equal(value.stage, null);
    assert.equal(value.title, "판단 불가");
  }
});
test("legacy NORMAL remains normal without becoming observation or a fourth stage", () => {
  const value = riskLevel("NORMAL");
  assert.equal(value.title, "정상");
  assert.equal(value.legacy, true);
  assert.equal(value.stage, null);
  assert.equal(RISK_STEPS.includes("NORMAL"), false);
  assert.equal(
    recordRisk({ status: "COMPLETED", level: "NORMAL" }).label,
    "정상 · 이전 결과",
  );
});
test("unfinished and failed records do not display a completed risk assessment", () => {
  for (const status of [
    "CREATED",
    "QUESTIONNAIRE",
    "RETRY_REQUIRED",
    "__proto__",
    null,
  ]) {
    const value = recordRisk({ status, level: "OBSERVATION" });
    assert.equal(value.stage, null);
    assert.notEqual(
      value.label,
      recordRisk({ status: "COMPLETED", level: "OBSERVATION" }).label,
    );
  }
  assert.equal(
    recordRisk({ status: "COMPLETED", level: "EMERGENCY" }).stage,
    3,
  );
});
