import assert from "node:assert/strict";
import test from "node:test";

const { default: worker } = await import("../dist/server/index.js");
async function render(path) {
  const response = await worker.fetch(new Request(`http://localhost${path}`, { headers: { accept: "text/html" } }), { ASSETS: { fetch: async () => new Response("Not found", { status: 404 }) } }, { waitUntil() {}, passThroughOnException() {} });
  return { status: response.status, html: await response.text() };
}

test("homepage renders searchable notebook and working article destinations", async () => {
  const { status, html } = await render("/");
  assert.equal(status, 200);
  assert.match(html, /Making things/);
  assert.match(html, /aria-label="Search notes"/);
  for (const slug of ["the-quiet-craft", "a-writing-habit", "why-ktor", "boring-architecture"]) assert.ok(html.includes(`/writing/${slug}`));
  assert.doesNotMatch(html, /href="#(?:article|archive|more|github|rss)"/);
});

test("article route renders the requested note and reading navigation", async () => {
  const { status, html } = await render("/writing/why-ktor");
  assert.equal(status, 200);
  assert.match(html, /Why I reached for Ktor/);
  assert.match(html, /Keep the boundary understandable/);
  assert.match(html, /Back to the notebook/);
  assert.match(html, /KEEP EXPLORING/);
});

test("unknown notes return a real 404", async () => {
  const { status } = await render("/writing/not-a-real-note");
  assert.equal(status, 404);
});

test("login retains accessible credential fields", async () => {
  const { status, html } = await render("/login");
  assert.equal(status, 200);
  assert.match(html, /autoComplete="email"/i);
  assert.match(html, /autoComplete="current-password"/i);
  assert.match(html, /aria-label="Show password"/);
  assert.doesNotMatch(html, /Manage drafts, bookmarks/);
});
