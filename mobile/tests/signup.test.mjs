import test from "node:test";
import assert from "node:assert/strict";
import { completeSignup } from "../src/services/signup.js";

const policy = {
  requiredAvailable: true,
  trainingAvailable: true,
  ...Object.fromEntries(
    ["terms", "privacy", "healthData", "location", "training"].map((key) => [
      key,
      { version: "test-version", url: "/policies/" + key },
    ]),
  ),
};
const required = {
  terms: true,
  privacy: true,
  health: true,
  location: false,
  training: false,
};

test("required-only signup records optional refusal and never requests location", async () => {
  const calls = [];
  await completeSignup({
    api: { post: async (...args) => calls.push(args) },
    policy,
    choices: required,
    requestLocationPermission: () =>
      assert.fail("Unexpected OS permission request"),
  });
  assert.equal(calls.length, 2);
  assert.equal(calls[0][1].agreedLocation, false);
  assert.deepEqual(calls[1][1], { granted: false, version: "test-version" });
  assert.ok(calls.every((call) => call[2] === "PUT"));
});
test("all selected consents persist before requesting foreground permission without collecting coordinates", async () => {
  const calls = [];
  const message = await completeSignup({
    api: { post: async (path, body) => calls.push({ path, body }) },
    policy,
    choices: { ...required, location: true, training: true },
    requestLocationPermission: async () => {
      calls.push({ permission: true });
      return { granted: true };
    },
  });
  assert.equal(calls.length, 3);
  assert.equal(calls[0].body.agreedLocation, true);
  assert.equal(calls[1].body.granted, true);
  assert.equal(calls[2].permission, true);
  assert.match(message, /가입을 완료/);
  assert.equal(JSON.stringify(calls).includes("latitude"), false);
});
test("OS denial or permission error does not fail completed signup", async () => {
  for (const request of [
    async () => ({ granted: false }),
    async () => {
      throw new Error("OS unavailable");
    },
  ]) {
    const message = await completeSignup({
      api: { post: async () => {} },
      policy,
      choices: { ...required, location: true },
      requestLocationPermission: request,
    });
    assert.match(message, /가입/);
    assert.match(message, /권한/);
  }
});
test("failed required or training persistence never opens OS permission or reports signup success", async () => {
  for (const failAt of [1, 2]) {
    let calls = 0;
    await assert.rejects(
      completeSignup({
        api: {
          post: async () => {
            if (++calls === failAt) throw new Error("save failed");
          },
        },
        policy,
        choices: { ...required, location: true, training: true },
        requestLocationPermission: () =>
          assert.fail("Permission before persistence"),
      }),
    );
  }
});
test("unavailable optional policy cannot be accepted and does not block required-only signup", async () => {
  const unavailable = {
    ...policy,
    trainingAvailable: false,
    training: { version: "test-version", url: null },
  };
  await assert.rejects(
    completeSignup({
      api: { post: () => assert.fail("Invalid consent sent") },
      policy: unavailable,
      choices: { ...required, training: true },
    }),
  );
  const calls = [];
  await completeSignup({
    api: { post: async (...args) => calls.push(args) },
    policy: unavailable,
    choices: required,
  });
  assert.equal(calls.length, 1);
});
