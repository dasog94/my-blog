import { resolveApiUrl } from "../../lib/api-config.mjs";

if (process.env.E2E_DEPLOYED !== undefined && process.env.E2E_DEPLOYED !== "1") {
  throw new Error("E2E_DEPLOYED must be unset for local tests or 1 for deployed tests.");
}

export const deployed = process.env.E2E_DEPLOYED === "1";

function required(name: string) {
  const value = process.env[name];
  if (!value?.trim()) throw new Error(`${name} is required for deployed login tests.`);
  return value;
}

function deployedOrigin(name: string) {
  const value = required(name);
  try {
    return resolveApiUrl(value, "production");
  } catch {
    throw new Error(`${name} must be an HTTPS origin with a non-local host, without credentials, a path, query, or fragment.`);
  }
}

// Reserved addresses and disposable credentials are for local tests only.
// Deployed tests never infer addresses or fall back to the local fixture.
export const targets = deployed ? {
  frontend: deployedOrigin("E2E_FRONTEND_URL"),
  api: deployedOrigin("E2E_API_URL"),
  email: required("E2E_EMAIL").trim(),
  password: required("E2E_PASSWORD"),
} : {
  frontend: "http://127.0.0.1:3000",
  api: "http://127.0.0.1:8080",
  email: "browser-test@example.test",
  password: "local-browser-test-password",
};

if (targets.frontend === targets.api) {
  throw new Error("Login integration tests require distinct frontend and API origins to exercise CORS.");
}
if (targets.password.length < 8) {
  throw new Error("E2E_PASSWORD must satisfy the login form's minimum length of 8 characters.");
}
