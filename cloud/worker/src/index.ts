/**
 * Helium Cloud: a thin Cloudflare Worker between the phone and third-party APIs.
 *
 * Why it exists: provider keys (DeepSeek, OpenCode Go, Gemini) live here as Worker secrets instead of on the phone,
 * and the Worker gives the app one reachable origin (a custom domain on Cloudflare) when a provider's own host is
 * slow or unreachable from the user's network. It stores nothing: every request is forwarded and streamed back.
 *
 * Routes (all but /health need `Authorization: Bearer <device token>`):
 *   GET  /health                      what is configured (no secrets)
 *   POST /v1/tools/search             DeepSeek Search API, same body and response as api.deepseek.com
 *   POST /v1/tools/fetch              DeepSeek Fetch API
 *   GET  /v1/tools/feed?url=...       fetch one public feed/page URL (SSRF-guarded, see assertPublicUrl)
 *   POST /mcp/exa                     Exa's public MCP endpoint (JSON-RPC body passed through)
 *   ANY  /llm/<provider>/<path>       model APIs with the key injected: deepseek, opencode-go, gemini
 *
 * Request logging is count-only (method, path, status, duration — never body, query, headers, tokens
 * or keys) and on by default; set REQUEST_LOG=0 to turn it off. The per-isolate limiter is a brake,
 * not a global quota (see README).
 */

export interface Env {
  HELIUM_TOKENS?: string
  DEEPSEEK_API_KEY?: string
  OPENCODE_API_KEY?: string
  GEMINI_API_KEY?: string
  EXA_API_KEY?: string
  RATE_PER_MINUTE?: string
  /** Count-only request log: "1" (default when unset) logs, "0" disables it. */
  REQUEST_LOG?: string
}

export const VERSION = "0.1.0"

const DEEPSEEK = "https://api.deepseek.com"
const EXA_MCP = "https://mcp.exa.ai/mcp"
const MAX_BODY = 4 * 1024 * 1024

type Upstream = { base: string; key: keyof Env; auth: (key: string, h: Headers) => void }

export const LLM_UPSTREAMS: Record<string, Upstream> = {
  deepseek: {
    base: DEEPSEEK,
    key: "DEEPSEEK_API_KEY",
    auth: (k, h) => {
      h.set("Authorization", `Bearer ${k}`)
      h.set("x-api-key", k)
    },
  },
  "opencode-go": {
    base: "https://opencode.ai/inference/go",
    key: "OPENCODE_API_KEY",
    auth: (k, h) => {
      h.set("Authorization", `Bearer ${k}`)
      h.set("x-api-key", k)
    },
  },
  gemini: {
    base: "https://generativelanguage.googleapis.com",
    key: "GEMINI_API_KEY",
    auth: (k, h) => h.set("x-goog-api-key", k),
  },
}

/** Headers never forwarded upstream: the device's own credentials and hop-by-hop / Cloudflare headers. */
const STRIP = ["authorization", "x-api-key", "x-goog-api-key", "host", "cookie", "cf-connecting-ip", "x-forwarded-for", "x-real-ip"]

export const json = (status: number, body: unknown, extra: Record<string, string> = {}) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json", ...extra } })

/** OpenAI-style error body, the shape the app already parses for every provider. */
export const error = (status: number, message: string, code = "helium_error") =>
  json(status, { error: { message, type: code, code } })

/** Constant-time string compare so token checks don't leak length-prefix timing. */
export function safeEqual(a: string, b: string): boolean {
  const enc = new TextEncoder()
  const x = enc.encode(a)
  const y = enc.encode(b)
  let diff = x.length ^ y.length
  for (let i = 0; i < Math.max(x.length, y.length); i++) diff |= (x[i] ?? 0) ^ (y[i] ?? 0)
  return diff === 0
}

export function tokenOf(req: Request): string {
  const h = req.headers.get("Authorization") ?? ""
  return h.startsWith("Bearer ") ? h.slice(7).trim() : ""
}

export function authorized(req: Request, env: Env): boolean {
  const token = tokenOf(req)
  if (!token) return false
  const allowed = (env.HELIUM_TOKENS ?? "").split(",").map((t) => t.trim()).filter(Boolean)
  let ok = false
  for (const t of allowed) ok = safeEqual(token, t) || ok
  return ok
}

/**
 * Per-isolate fixed-window limiter. Cloudflare runs many isolates, so this is a brake on a runaway agent loop on one
 * device, not a hard quota; providers enforce their own limits behind it.
 */
export class RateLimiter {
  private windows = new Map<string, { start: number; count: number }>()
  private perMinute: number
  private now: () => number
  constructor(perMinute: number, now: () => number = Date.now) {
    this.perMinute = perMinute
    this.now = now
  }
  take(key: string): boolean {
    const t = this.now()
    const w = this.windows.get(key)
    if (!w || t - w.start >= 60_000) {
      this.windows.set(key, { start: t, count: 1 })
      if (this.windows.size > 10_000) this.windows.clear()
      return true
    }
    w.count++
    return w.count <= this.perMinute
  }
}

let limiter: RateLimiter | null = null

function forwardHeaders(req: Request): Headers {
  const h = new Headers()
  req.headers.forEach((v, k) => {
    if (!STRIP.includes(k.toLowerCase())) h.set(k, v)
  })
  return h
}

/** Streams the upstream response back; SSE bodies pass through untouched so tokens arrive as they are generated. */
function passthrough(up: Response): Response {
  const h = new Headers()
  for (const k of ["content-type", "cache-control", "x-ds-request-id", "retry-after"]) {
    const v = up.headers.get(k)
    if (v) h.set(k, v)
  }
  h.set("x-helium-upstream-status", String(up.status))
  return new Response(up.body, { status: up.status, headers: h })
}

async function boundedBody(req: Request): Promise<ArrayBuffer | null | "too_large"> {
  if (req.method === "GET" || req.method === "HEAD") return null
  const len = Number(req.headers.get("content-length") ?? "0")
  if (len > MAX_BODY) return "too_large"
  const buf = await req.arrayBuffer()
  return buf.byteLength > MAX_BODY ? "too_large" : buf
}

async function forward(req: Request, target: string, auth: (h: Headers) => void, fetcher: typeof fetch): Promise<Response> {
  const body = await boundedBody(req)
  if (body === "too_large") return error(413, "request body too large")
  const headers = forwardHeaders(req)
  auth(headers)
  try {
    const up = await fetcher(target, { method: req.method, headers, body })
    return passthrough(up)
  } catch (e) {
    return error(502, `upstream unreachable: ${e instanceof Error ? e.message : String(e)}`, "upstream_error")
  }
}

export async function handle(req: Request, env: Env, fetcher: typeof fetch = fetch): Promise<Response> {
  const started = Date.now()
  const res = await route(req, env, fetcher)
  if (requestLogEnabled(env)) {
    // Count-only: path without query, no headers/body/token/key anywhere in the line.
    console.log(requestLogLine(req.method, new URL(req.url).pathname, res.status, Date.now() - started))
  }
  return res
}

/** Count-only request log, on by default; set REQUEST_LOG=0 to turn it off. */
export function requestLogEnabled(env: Env): boolean {
  return (env.REQUEST_LOG ?? "1").trim() !== "0"
}

/**
 * One JSON log line per request: method, path, status, duration in ms. Nothing else — no query
 * string (it can carry a target URL), no headers, no body, no device token, no provider key.
 */
export function requestLogLine(method: string, path: string, status: number, ms: number): string {
  return JSON.stringify({ type: "helium_request", method, path, status, ms })
}

/**
 * SSRF guard for user-supplied fetch targets. This is the same denylist as the Android custom-card
 * check (CardDefinition.checkUrl in glance/Cards.kt) — one rule, two runtimes. It deliberately does
 * not do DNS resolution: a hostname that resolves to a private address after the check is still
 * possible, so treat fetched content as untrusted either way.
 */
export function isPrivateHost(host: string): boolean {
  const h = host.toLowerCase()
  return (
    h === "localhost" ||
    h.endsWith(".local") ||
    h.startsWith("127.") ||
    h.startsWith("10.") ||
    h.startsWith("192.168.") ||
    /^172\.(1[6-9]|2\d|3[01])\./.test(h) ||
    h === "0.0.0.0"
  )
}

/** Parses [raw] and rejects anything that isn't a public http(s) URL. Throws with a safe message. */
export function assertPublicUrl(raw: string): URL {
  let u: URL
  try {
    u = new URL(raw)
  } catch {
    throw new Error("invalid url")
  }
  if (u.protocol !== "http:" && u.protocol !== "https:") throw new Error("only http(s) urls are allowed")
  if (isPrivateHost(u.hostname)) throw new Error("private hosts are not allowed")
  return u
}

async function route(req: Request, env: Env, fetcher: typeof fetch = fetch): Promise<Response> {
  const url = new URL(req.url)
  const path = url.pathname.replace(/\/+$/, "") || "/"

  if (path === "/health" && req.method === "GET") {
    return json(200, {
      ok: true,
      version: VERSION,
      search: { exa: true, deepseek: Boolean(env.DEEPSEEK_API_KEY) },
      llm: Object.fromEntries(Object.entries(LLM_UPSTREAMS).map(([id, u]) => [id, Boolean(env[u.key])])),
    })
  }

  if (!authorized(req, env)) return error(401, "missing or invalid device token", "unauthorized")

  limiter ??= new RateLimiter(Number(env.RATE_PER_MINUTE ?? "120") || 120)
  if (!limiter.take(tokenOf(req))) return error(429, "too many requests, slow down", "rate_limited")

  if (path === "/v1/tools/search" || path === "/v1/tools/fetch") {
    if (req.method !== "POST") return error(405, "use POST")
    const key = env.DEEPSEEK_API_KEY
    if (!key) return error(503, "DeepSeek search is not configured on this Worker", "not_configured")
    return forward(req, DEEPSEEK + path, (h) => h.set("Authorization", `Bearer ${key}`), fetcher)
  }

  if (path === "/mcp/exa") {
    if (req.method !== "POST") return error(405, "use POST")
    // The key travels as an x-api-key header, never in the URL: query strings land in access logs
    // on both sides. Exa's MCP server reads x-api-key first (then Authorization, then exaApiKey).
    return forward(req, EXA_MCP, (h) => {
      h.set("Accept", "application/json, text/event-stream")
      if (env.EXA_API_KEY) h.set("x-api-key", env.EXA_API_KEY)
    }, fetcher)
  }

  if (path === "/v1/tools/feed") {
    if (req.method !== "GET") return error(405, "use GET")
    const raw = url.searchParams.get("url")
    if (!raw) return error(400, "missing ?url=")
    let target: URL
    try {
      target = assertPublicUrl(raw)
    } catch (e) {
      return error(400, e instanceof Error ? e.message : "bad url", "bad_url")
    }
    try {
      const up = await fetcher(target.toString(), {
        headers: {
          "User-Agent": "helium-cloud feed fetch",
          Accept: "application/rss+xml, application/atom+xml, application/xml;q=0.9, text/html;q=0.8, */*;q=0.5",
        },
      })
      const len = Number(up.headers.get("content-length") ?? "0")
      if (len > MAX_BODY) return error(413, "feed too large")
      const h = new Headers()
      const ct = up.headers.get("content-type")
      if (ct) h.set("content-type", ct)
      return new Response(up.body, { status: up.status, headers: h })
    } catch (e) {
      return error(502, `feed unreachable: ${e instanceof Error ? e.message : String(e)}`, "upstream_error")
    }
  }

  const m = path.match(/^\/llm\/([a-z0-9-]+)(\/.*)$/)
  if (m) {
    const up = LLM_UPSTREAMS[m[1]]
    if (!up) return error(404, `unknown provider ${m[1]}`, "not_found")
    const key = env[up.key]
    if (!key) return error(503, `${m[1]} is not configured on this Worker`, "not_configured")
    if (m[2].includes("..")) return error(400, "bad path")
    return forward(req, up.base + m[2] + url.search, (h) => up.auth(key, h), fetcher)
  }

  return error(404, "not found", "not_found")
}

export default {
  fetch: (req: Request, env: Env) => handle(req, env),
}
