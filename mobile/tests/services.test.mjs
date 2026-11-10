import { readFile } from "node:fs/promises";
import { test } from "node:test";
import assert from "node:assert/strict";
async function moduleAt(name) {
  const code = await readFile(
    new URL("../src/" + name, import.meta.url),
    "utf8",
  );
  return import(
    `data:text/javascript;base64,${Buffer.from(code).toString("base64")}`
  );
}
const { createClient, ApiError } = await moduleAt("services/client.js");
const { scenarioKey } = await moduleAt("services/scenarios.js");
test("member/guest bearer and exact answer version/request ID reach the existing contract", async () => {
  const seen = [];
  let token = "guest-token";
  const api = createClient(
    "https://example.test/",
    () => token,
    async (url, options) => {
      seen.push({ url, ...options });
      return new Response(JSON.stringify({ id: "one" }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    },
  );
  const answer = {
    clientRequestId: "same-retry-id",
    expectedVersion: 7,
    questionId: "q1",
    answer: "none",
    userText: "",
  };
  await api.answer("one", answer);
  await api.answer("one", answer);
  assert.equal(seen[0].headers.get("Authorization"), "Bearer guest-token");
  assert.equal(seen[0].body, seen[1].body);
  assert.deepEqual(JSON.parse(seen[0].body), answer);
  token = "member-token";
  await api.list(1, "same/case");
  assert.equal(seen[2].headers.get("Authorization"), "Bearer member-token");
  assert.match(seen[2].url, /caseId=same%2Fcase/);
});
test("server rejection and network failure never become successful empty results", async () => {
  const forbidden = createClient(
    "https://example.test",
    () => null,
    async () =>
      new Response(JSON.stringify({ message: "동의 필요" }), { status: 403 }),
  );
  await assert.rejects(
    forbidden.list(),
    (e) =>
      e instanceof ApiError && e.status === 403 && e.message === "동의 필요",
  );
  const offline = createClient(
    "https://example.test",
    () => null,
    async () => {
      throw Error("offline");
    },
  );
  await assert.rejects(
    offline.list(),
    (e) => e instanceof ApiError && e.status === 0,
  );
});
test("normal_skin and uncertain scenario do not fall into a wrong type-specific explanation", () => {
  assert.equal(
    scenarioKey({
      slots: {},
      vision: { classification: { label: "normal_skin" } },
    }),
    "normal",
  );
  assert.equal(
    scenarioKey({
      slots: { scenario: "unsure" },
      vision: { classification: { label: "burn" } },
    }),
    "unknown",
  );
  assert.equal(
    scenarioKey({
      slots: { scenario: "bruise" },
      vision: { classification: { label: "UNKNOWN" } },
    }),
    "bruise",
  );
});
