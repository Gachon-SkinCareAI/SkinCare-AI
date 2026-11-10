import { readFile } from "node:fs/promises";
import { test } from "node:test";
import assert from "node:assert/strict";
const code = await readFile(
  new URL("../src/utils/draft.js", import.meta.url),
  "utf8",
);
const { validateDate, validatePhoto, today } = await import(
  `data:text/javascript;base64,${Buffer.from(code).toString("base64")}`
);
test("날짜 형식, 윤년, 실제 날짜, 미래 날짜 검증", () => {
  assert.equal(validateDate("2024-02-29"), "");
  assert.ok(validateDate("2025-02-29"));
  assert.ok(validateDate("2026-02-30"));
  assert.ok(validateDate("2026-2-01"));
  assert.ok(validateDate("9999-01-01"));
  assert.equal(validateDate(today()), "");
});
test("사진 없음, 형식, 파일 크기 경계 검증", () => {
  assert.ok(validatePhoto(null));
  assert.ok(validatePhoto({ uri: "local", mimeType: "image/gif" }));
  assert.ok(validatePhoto({ uri: "local", fileSize: 10485761 }));
  assert.equal(
    validatePhoto({ uri: "local", fileSize: 10485760, mimeType: "image/png" }),
    "",
  );
  assert.equal(validatePhoto({ uri: "local", mimeType: "image/heic" }), "");
});
