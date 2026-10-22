import { defineConfig, devices } from "@playwright/test";
import { deployed, targets } from "./tests/e2e/targets";

export default defineConfig({
  testDir: "./tests/e2e",
  testMatch: "**/*.spec.ts",
  fullyParallel: false,
  workers: 1,
  retries: 0,
  forbidOnly: Boolean(process.env.CI),
  timeout: deployed ? 120_000 : 60_000,
  globalTimeout: 10 * 60_000,
  expect: { timeout: 10_000 },
  reporter: "list",
  use: {
    baseURL: targets.frontend,
    // Authentication traces can contain passwords and JWTs. Do not persist them.
    trace: "off",
    screenshot: "off",
    video: "off",
    actionTimeout: 15_000,
    navigationTimeout: 45_000,
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  webServer: deployed ? undefined : [
    {
      command: "./build/install/jimmy-blog-api/bin/jimmy-blog-api",
      cwd: "../backend",
      url: `${targets.api}/health`,
      timeout: 60_000,
      reuseExistingServer: false,
      env: {
        APP_ENV: "development",
        PORT: "8080",
        CORS_ALLOWED_ORIGINS: targets.frontend,
        JWT_SECRET: "disposable-jwt-secret-for-local-browser-tests-only",
        BLOG_ACCOUNT_ID: "usr_browser_test",
        BLOG_ACCOUNT_NAME: "Browser Test",
        BLOG_ACCOUNT_EMAIL: targets.email,
        BLOG_ACCOUNT_PASSWORD: targets.password,
      },
      gracefulShutdown: { signal: "SIGTERM", timeout: 5_000 },
    },
    {
      command: "npm run dev -- --hostname 0.0.0.0 --port 3000",
      url: `${targets.frontend}/login`,
      timeout: 120_000,
      reuseExistingServer: false,
      env: {
        NODE_ENV: "development",
        NEXT_PUBLIC_API_URL: targets.api,
      },
      gracefulShutdown: { signal: "SIGTERM", timeout: 5_000 },
    },
  ],
});
