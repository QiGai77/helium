import { test } from "node:test"
import assert from "node:assert/strict"
import { handle, RateLimiter, safeEqual, type Env } from "../src/index.ts"

const env: Env = { HELIUM_TOKENS: "dev-a, dev-b", DEEPSEEK_API_KEY: "sk-ds", OPENCODE_API_KEY: "sk-oc" }

type Seen = { url: string; init: RequestInit & { headers: Headers } }
function fakeFetch(respond: (s: Seen) => Response = () => new Response("{}", { headers: { "content-type": "application/json" } })) {
  const seen: Seen[] = []
  const f = (async (url: string, init: any) => {
    const s = { url, init }
    seen.push(s)
    return respond(s)
  }) as unknown as typeof fetch
  return { f, seen }
}

const req = (path: string, init: RequestInit = {}, token: string | null = "dev-a") => {
  const h = new Headers(init.headers)
  if (token) h.set("Authorization", `Bearer ${token}`)
  return new Request("https://cloud.example" + path, { ...init, headers: h })
}

test("health is public and reports configuration without secrets", async () => {
  const r = await handle(req("/health", {}, null), env)
  assert.equal(r.status, 200)
  const body = await r.json()
  assert.deepEqual(body.search, { exa: true, deepseek: true })
  assert.deepEqual(body.llm, { deepseek: true, "opencode-go": true, gemini: false })
  assert.ok(!JSON.stringify(body).includes("sk-"))
})

test("rejects missing and wrong tokens", async () => {
  const { f, seen } = fakeFetch()
  assert.equal((await handle(req("/v1/tools/search", { method: "POST", body: "{}" }, null), env, f)).status, 401)
  assert.equal((await handle(req("/v1/tools/search", { method: "POST", body: "{}" }, "dev-c"), env, f)).status, 401)
  assert.equal((await handle(req("/v1/tools/search", { method: "POST", body: "{}" }, "dev-"), env, f)).status, 401)
  assert.equal(seen.length, 0)
})

test("search is forwarded to DeepSeek with the server key, never the device token", async () => {
  const { f, seen } = fakeFetch(() => new Response(JSON.stringify({ results: [{ title: "t", url: "https://x" }] }), { headers: { "content-type": "application/json" } }))
  const r = await handle(req("/v1/tools/search", { method: "POST", body: JSON.stringify({ query: "q" }), headers: { "Content-Type": "application/json", Cookie: "c=1" } }, "dev-b"), env, f)
  assert.equal(r.status, 200)
  assert.equal(seen[0].url, "https://api.deepseek.com/v1/tools/search")
  assert.equal(seen[0].init.headers.get("Authorization"), "Bearer sk-ds")
  assert.equal(seen[0].init.headers.get("cookie"), null)
  assert.equal((await r.json()).results[0].url, "https://x")
})

test("search without a DeepSeek key is 503, not a silent pass-through", async () => {
  const { f, seen } = fakeFetch()
  const r = await handle(req("/v1/tools/search", { method: "POST", body: "{}" }), { HELIUM_TOKENS: "dev-a" }, f)
  assert.equal(r.status, 503)
  assert.equal(seen.length, 0)
})

test("exa MCP proxy passes the JSON-RPC body and SSE response through", async () => {
  const sse = 'event: message\ndata: {"result":{"content":[{"type":"text","text":"Title: A\\nURL: https://a"}]}}\n\n'
  const { f, seen } = fakeFetch(() => new Response(sse, { headers: { "content-type": "text/event-stream" } }))
  const rpc = JSON.stringify({ jsonrpc: "2.0", id: 1, method: "tools/call", params: { name: "web_search_exa", arguments: { query: "q" } } })
  const r = await handle(req("/mcp/exa", { method: "POST", body: rpc }), env, f)
  assert.equal(seen[0].url, "https://mcp.exa.ai/mcp")
  assert.equal(seen[0].init.headers.get("authorization"), null)
  assert.equal(r.headers.get("content-type"), "text/event-stream")
  assert.equal(await r.text(), sse)
})

test("exa key goes in an x-api-key header, never in the URL", async () => {
  const { f, seen } = fakeFetch()
  const keyed: Env = { ...env, EXA_API_KEY: "sk-exa-secret" }
  const r = await handle(req("/mcp/exa", { method: "POST", body: "{}" }), keyed, f)
  assert.equal(r.status, 200)
  assert.equal(seen[0].url, "https://mcp.exa.ai/mcp")
  assert.ok(!seen[0].url.includes("sk-exa-secret") && !seen[0].url.includes("exaApiKey"))
  assert.equal(seen[0].init.headers.get("x-api-key"), "sk-exa-secret")
})

test("feed route fetches a public URL and rejects private hosts (same denylist as Android cards)", async () => {
  const rss = '<?xml version="1.0"?><rss version="2.0"><channel><title>T</title></channel></rss>'
  const { f, seen } = fakeFetch(() => new Response(rss, { headers: { "content-type": "application/rss+xml" } }))
  const ok = await handle(req("/v1/tools/feed?url=" + encodeURIComponent("https://example.com/feed.xml")), env, f)
  assert.equal(ok.status, 200)
  assert.equal(ok.headers.get("content-type"), "application/rss+xml")
  assert.equal(await ok.text(), rss)
  assert.equal(seen[0].url, "https://example.com/feed.xml")
  assert.equal(seen.length, 1)

  const bad = [
    "http://localhost:8080/admin",
    "http://127.0.0.1/feed",
    "http://10.0.0.5/feed",
    "http://192.168.1.1/feed",
    "http://172.16.0.9/feed",
    "http://172.31.255.1/feed",
    "http://0.0.0.0/feed",
    "http://printer.local/feed",
    "ftp://example.com/feed",
    "file:///etc/passwd",
  ]
  for (const u of bad) {
    const r = await handle(req("/v1/tools/feed?url=" + encodeURIComponent(u)), env, f)
    assert.equal(r.status, 400, u)
  }
  assert.equal(seen.length, 1) // none of the rejected URLs was ever fetched
  assert.equal((await handle(req("/v1/tools/feed"), env, f)).status, 400)
  assert.equal((await handle(req("/v1/tools/feed?url=" + encodeURIComponent("https://example.com/feed")), env, f)).status, 200)
  assert.equal((await handle(req("/v1/tools/feed?url=" + encodeURIComponent("https://example.com/feed"), { method: "POST" }), env, f)).status, 405)
  assert.equal((await handle(req("/v1/tools/feed?url=" + encodeURIComponent("https://example.com/feed"), {}, null), env, f)).status, 401)
})

test("request log is count-only and off when REQUEST_LOG=0", async () => {
  const lines: string[] = []
  const orig = console.log
  console.log = (...a: unknown[]) => { lines.push(String(a[0])) }
  try {
    const { f } = fakeFetch()
    await handle(req("/health", {}, null), env, f)
    await handle(req("/v1/tools/search", { method: "POST", body: "{}" }, "dev-a"), { ...env, REQUEST_LOG: "0" }, f)
    assert.equal(lines.length, 1)
    const line = JSON.parse(lines[0])
    assert.equal(line.type, "helium_request")
    assert.equal(line.method, "GET")
    assert.equal(line.path, "/health")
    assert.equal(line.status, 200)
    assert.equal(typeof line.ms, "number")
    assert.ok(!lines[0].includes("dev-a") && !lines[0].includes("sk-"))
    assert.deepEqual(Object.keys(line).sort(), ["method", "ms", "path", "status", "type"])
  } finally {
    console.log = orig
  }
})

test("llm proxy injects provider keys and keeps path and query", async () => {
  const { f, seen } = fakeFetch()
  await handle(req("/llm/opencode-go/openai/v1/chat/completions?x=1", { method: "POST", body: "{}", headers: { "x-opencode-session": "s1" } }), env, f)
  assert.equal(seen[0].url, "https://opencode.ai/inference/go/openai/v1/chat/completions?x=1")
  assert.equal(seen[0].init.headers.get("authorization"), "Bearer sk-oc")
  assert.equal(seen[0].init.headers.get("x-opencode-session"), "s1")
  assert.equal((await handle(req("/llm/gemini/v1beta/models", {}), env, f)).status, 503)
  assert.equal((await handle(req("/llm/nope/x", {}), env, f)).status, 404)
})

test("upstream errors keep their status; network failure becomes 502", async () => {
  const { f } = fakeFetch(() => new Response('{"error":{"message":"bal"}}', { status: 402, headers: { "content-type": "application/json" } }))
  const r = await handle(req("/v1/tools/search", { method: "POST", body: "{}" }), env, f)
  assert.equal(r.status, 402)
  const boom = (async () => { throw new Error("dns") }) as unknown as typeof fetch
  assert.equal((await handle(req("/v1/tools/fetch", { method: "POST", body: "{}" }), env, boom)).status, 502)
})

test("rate limiter allows N per minute per key and resets", () => {
  let t = 0
  const l = new RateLimiter(2, () => t)
  assert.ok(l.take("a")); assert.ok(l.take("a")); assert.ok(!l.take("a")); assert.ok(l.take("b"))
  t = 60_000
  assert.ok(l.take("a"))
})

test("safeEqual", () => {
  assert.ok(safeEqual("abc", "abc"))
  assert.ok(!safeEqual("abc", "abd"))
  assert.ok(!safeEqual("abc", "abcd"))
})
