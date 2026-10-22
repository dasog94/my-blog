import assert from "node:assert/strict";
import { mkdtempSync, rmSync, symlinkSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import test from "node:test";

const frontendRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");

test("the real production build rejects invalid API configuration before compiling", (t) => {
  // Import the checked-in Vite config from an empty project directory. This
  // exercises the real Vinext build entry point without reading a developer's
  // .env.local or other deployment-specific files from the checkout.
  const project = mkdtempSync(join(tmpdir(), "blog-build-config-"));
  t.after(() => rmSync(project, { recursive: true, force: true }));
  writeFileSync(join(project, "package.json"), JSON.stringify({ type: "module" }));
  writeFileSync(join(project, "vite.config.ts"),
    `export { default } from ${JSON.stringify(join(frontendRoot, "vite.config.ts"))};\n`);
  symlinkSync(join(frontendRoot, "node_modules"), join(project, "node_modules"), "junction");

  for (const [label, value, expected] of [
    ["missing", undefined, /NEXT_PUBLIC_API_URL is required/],
    ["blank", "   ", /NEXT_PUBLIC_API_URL is required/],
    ["localhost", "https://localhost:8080", /NEXT_PUBLIC_API_URL must use HTTPS and a non-local host/],
  ]) {
    const env = { ...process.env, NODE_ENV: "production" };
    delete env.NEXT_PUBLIC_API_URL;
    if (value !== undefined) env.NEXT_PUBLIC_API_URL = value;
    const result = spawnSync(process.execPath,
      [join(frontendRoot, "node_modules/vinext/dist/cli.js"), "build"],
      { cwd: project, env, encoding: "utf8", timeout: 30_000 },
    );
    assert.ifError(result.error);
    assert.notEqual(result.status, 0, `${label}: production build unexpectedly succeeded`);
    // A dependency, syntax, or other unrelated build failure must fail this test.
    assert.match(`${result.stdout}\n${result.stderr}`, expected, `${label}: wrong build failure`);
  }
});
