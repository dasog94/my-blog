import assert from "node:assert/strict";
import test from "node:test";
import { resolveApiUrl } from "../lib/api-config.mjs";

test("only local development may omit the API origin", () => {
  assert.equal(resolveApiUrl(undefined, "development"), "http://localhost:8080");
  for (const environment of ["production", "test", undefined]) {
    assert.throws(() => resolveApiUrl(undefined, environment), /NEXT_PUBLIC_API_URL is required/);
  }
  for (const value of ["", "   "]) {
    assert.throws(() => resolveApiUrl(value, "development"), /NEXT_PUBLIC_API_URL is required/);
  }
});

test("normalizes a supplied origin and keeps an explicit non-default port", () => {
  assert.equal(resolveApiUrl(" https://api.example.test/ ", "production"), "https://api.example.test");
  assert.equal(resolveApiUrl("https://api.example.test:8443/", "production"), "https://api.example.test:8443");
  assert.equal(resolveApiUrl("http://127.0.0.1:8081/", "development"), "http://127.0.0.1:8081");
});

test("rejects URLs that cannot safely serve as the API origin", () => {
  for (const value of [
    "/api", "api.example.test", "https:api.example.test", "ftp://api.example.test",
    "https://user:password@api.example.test", "https://@api.example.test",
    "https://api.example.test/api", "https://api.example.test//",
    "https://api.example.test/api/..", "https://api.example.test/.",
    "https://api.example.test?key=value", "https://api.example.test?",
    "https://api.example.test/#fragment", "https://api.example.test/#",
    "https://api.example.test\\api", "https://*.example.test",
    "https://api.example.test:65536", "https://api.example.test:abc",
    "https://api.example.test:", "https://api.example.test:0",
  ]) {
    assert.throws(() => resolveApiUrl(value, "production"), /NEXT_PUBLIC_API_URL/, value);
  }
});

test("production rejects HTTP, loopback aliases, and unspecified bind addresses", () => {
  for (const value of [
    "http://api.example.test", "http://localhost:8080", "https://localhost:8080",
    "https://LOCALHOST.", "https://api.localhost", "https://127.0.0.1",
    "https://127.12.34.56", "https://127.1", "https://2130706433", "https://0x7f000001",
    "https://0.0.0.0", "https://[::1]", "https://[::]",
    "https://[::ffff:127.0.0.1]", "https://[::ffff:127.12.34.56]", "https://[::ffff:0.0.0.0]",
  ]) {
    assert.throws(() => resolveApiUrl(value, "production"), /HTTPS and a non-local host/, value);
  }
});
