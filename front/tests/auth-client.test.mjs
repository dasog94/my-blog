import assert from "node:assert/strict";
import test from "node:test";

// Reserved test-only address. These tests intercept fetch; no credentials leave
// the test process and this address is never used as a deployment default.
process.env.NODE_ENV = "production";
process.env.NEXT_PUBLIC_API_URL = "https://api.example.test/";
const { login, getCurrentUser } = await import("../app/auth.ts");
const user = { id: "test-user", name: "Test User", email: "reader@example.test" };

test("login sends JSON to the configured origin and returns the session", async (t) => {
  const session = { token: "test-token", user };
  t.mock.method(globalThis, "fetch", async (url, options) => {
    assert.equal(url, "https://api.example.test/api/auth/login");
    assert.equal(options.method, "POST");
    assert.equal(options.headers["Content-Type"], "application/json");
    assert.deepEqual(JSON.parse(options.body), { email: user.email, password: "test-password" });
    return Response.json(session);
  });
  assert.deepEqual(await login(user.email, "test-password"), session);
});

test("invalid credentials preserve the API's readable 401 message", async (t) => {
  t.mock.method(globalThis, "fetch", async () => Response.json(
    { message: "Email or password is incorrect." }, { status: 401 },
  ));
  await assert.rejects(login(user.email, "wrong-password"), /Email or password is incorrect\./);
});

test("a non-JSON service failure does not mask the login error", async (t) => {
  t.mock.method(globalThis, "fetch", async () => new Response("Bad gateway", { status: 502 }));
  await assert.rejects(login(user.email, "test-password"), /Unable to sign in\./);
});

test("network failures reject login rather than creating a session", async (t) => {
  t.mock.method(globalThis, "fetch", async () => { throw new TypeError("Failed to fetch"); });
  await assert.rejects(login(user.email, "test-password"), /Failed to fetch/);
});

test("current-user requests use the configured origin and Bearer token", async (t) => {
  t.mock.method(globalThis, "fetch", async (url, options) => {
    assert.equal(url, "https://api.example.test/api/auth/me");
    assert.deepEqual(options.headers, { Authorization: "Bearer test-token" });
    return Response.json(user);
  });
  assert.deepEqual(await getCurrentUser("test-token"), user);
});

test("an invalid token rejects the current-user request", async (t) => {
  t.mock.method(globalThis, "fetch", async () => new Response(null, { status: 401 }));
  await assert.rejects(getCurrentUser("expired-token"), /Your session has expired\./);
});
