import { randomUUID } from "node:crypto";
import { expect, test, type Page } from "@playwright/test";
import { deployed, targets } from "./targets";

const sessionKey = "jimmy-blog.auth-session";
const loginUrl = `${targets.api}/api/auth/login`;

async function openLogin(page: Page, url = "/login") {
  await page.goto(url);
  // The server-rendered form can appear before its React handlers are ready.
  // Observe an actual state change rather than racing hydration or sleeping.
  const password = page.getByLabel("Password", { exact: true });
  await expect(async () => {
    if (await password.getAttribute("type") === "password") {
      await page.getByRole("button", { name: "Show password" }).click();
    }
    await expect(password).toHaveAttribute("type", "text", { timeout: 1_000 });
  }).toPass({ timeout: 20_000 });
  await page.getByRole("button", { name: "Hide password" }).click();
  await expect(password).toHaveAttribute("type", "password");
}

async function submitLogin(page: Page, password: string) {
  // Permit the real API request, but stop a stale/misconfigured browser bundle
  // from sending a deployed account's credentials to a different destination.
  let unexpectedDestination = false;
  await page.route(/\/api\/auth\/login(?:[?#].*)?$/, async (route) => {
    if (route.request().url() !== loginUrl) {
      unexpectedDestination = true;
      await route.abort("blockedbyclient");
      return;
    }
    await route.continue();
  });
  await page.getByLabel("Email address").fill(targets.email);
  await page.getByLabel("Password", { exact: true }).fill(password);
  const request = page.waitForRequest((candidate) =>
    candidate.method() === "POST" && /\/api\/auth\/login(?:[?#].*)?$/.test(candidate.url()));
  await page.getByRole("button", { name: "Log in" }).click();
  const sent = await request;
  expect(sent.url(), "The frontend must use the configured API origin before sending credentials.").toBe(loginUrl);
  expect(unexpectedDestination).toBe(false);
}

test("API health and preflights allow the exact frontend origin", async ({ request }) => {
  const health = await request.get(`${targets.api}/health`, { timeout: 90_000 });
  expect(health.status()).toBe(200);
  expect(await health.text()).toBe("ok");

  for (const [path, method, header] of [
    ["/api/auth/login", "POST", "content-type"],
    ["/api/auth/me", "GET", "authorization"],
  ]) {
    const response = await request.fetch(`${targets.api}${path}`, {
      method: "OPTIONS",
      headers: {
        Origin: targets.frontend,
        "Access-Control-Request-Method": method,
        "Access-Control-Request-Headers": header,
      },
    });
    expect(response.ok(), `${method} ${path} preflight must succeed.`).toBe(true);
    expect(response.headers()["access-control-allow-origin"]).toBe(targets.frontend);
    expect(response.headers()["access-control-allow-headers"].toLowerCase()).toContain(header);
  }
});

test("browser login stores the session and its Bearer token authenticates /me", async ({ page }) => {
  await openLogin(page);
  const responsePromise = page.waitForResponse((response) =>
    response.url() === loginUrl && response.request().method() === "POST");
  await submitLogin(page, targets.password);
  const response = await responsePromise;
  expect(response.status()).toBe(200);
  expect(await response.headerValue("access-control-allow-origin")).toBe(targets.frontend);
  await expect(page).toHaveURL(`${targets.frontend}/`);
  await expect(page.getByRole("button", { name: "Log out" })).toBeVisible();

  const session = await page.evaluate((key) => {
    const stored = JSON.parse(sessionStorage.getItem(key) ?? "null");
    return { hasToken: typeof stored?.token === "string" && stored.token.length > 0, user: stored?.user };
  }, sessionKey);
  expect(session.hasToken).toBe(true);
  expect(session.user.email.toLowerCase()).toBe(targets.email.toLowerCase());
  await expect(page.locator(".account")).toContainText(session.user.name);

  // This fetch runs in the browser, so Authorization preflight and CORS are
  // enforced. Return only the user profile, never the token, to the test runner.
  const currentUser = await page.evaluate(async ({ api, key }) => {
    const { token } = JSON.parse(sessionStorage.getItem(key) ?? "null");
    const result = await fetch(`${api}/api/auth/me`, {
      headers: { Authorization: `Bearer ${token}` },
      redirect: "error",
    });
    return { status: result.status, user: result.ok ? await result.json() : null };
  }, { api: targets.api, key: sessionKey });
  expect(currentUser.status).toBe(200);
  expect(currentUser.user).toEqual(session.user);

  await page.getByRole("button", { name: "Log out" }).click();
  expect(await page.evaluate((key) => sessionStorage.getItem(key), sessionKey)).toBeNull();
  await expect(page.getByRole("link", { name: /^Log in/ })).toBeVisible();
});

test("incorrect credentials expose the API's 401 message to the browser", async ({ page }) => {
  await openLogin(page);
  const responsePromise = page.waitForResponse((response) =>
    response.url() === loginUrl && response.request().method() === "POST");
  await submitLogin(page, `incorrect-${randomUUID()}`);
  const response = await responsePromise;
  expect(response.status()).toBe(401);
  expect(await response.headerValue("access-control-allow-origin")).toBe(targets.frontend);
  await expect(page.getByRole("alert")).toHaveText("Email or password is incorrect.");
  expect(await page.evaluate((key) => sessionStorage.getItem(key), sessionKey)).toBeNull();
});

test("a different browser origin cannot use the login API", async ({ page }) => {
  test.skip(deployed, "Only the local fixture has a known second frontend origin.");
  // The same dev server, with a different origin that is absent from the API's
  // allowlist. The login request still goes to the real Ktor server.
  await openLogin(page, "http://localhost:3000/login");
  const rejected = page.waitForEvent("requestfailed", (request) =>
    request.url() === loginUrl && request.method() === "POST");
  await submitLogin(page, targets.password);
  await rejected;
  await expect(page.getByRole("alert")).toBeVisible();
  expect(await page.evaluate((key) => sessionStorage.getItem(key), sessionKey)).toBeNull();
});
