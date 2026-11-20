# Helium Cloud (Cloudflare Worker)

A stateless proxy between the Helium app and third-party APIs. It holds provider keys as Worker secrets so they never sit on the phone, and gives the app one reachable origin on your own domain.

| Route | What it does |
| --- | --- |
| `GET /health` | Public. Which providers are configured (never the keys). |
| `POST /v1/tools/search` · `/v1/tools/fetch` | DeepSeek Search / Fetch API, same body and response as `api.deepseek.com`. |
| `GET /v1/tools/feed?url=...` | Fetch one public feed/page URL (RSS/Atom/HTML) and return it as-is. The URL must be public http(s): localhost, `.local`, `127.*`, `10.*`, `192.168.*`, `172.16–31.*` and `0.0.0.0` are rejected — the same denylist as the Android custom-card check (`glance/Cards.kt`), one rule in two runtimes. The app does not use this route yet; it fetches feeds directly. |
| `POST /mcp/exa` | Exa's public MCP endpoint (keyless; `EXA_API_KEY` optional, sent as an `x-api-key` header, never in the URL). |
| `ANY /llm/<provider>/<path>` | Model APIs with the key injected: `deepseek`, `opencode-go`, `gemini`. Streams SSE through. |

Every route except `/health` needs `Authorization: Bearer <device token>`; tokens are the comma-separated `HELIUM_TOKENS` secret. The device's own `Authorization`, `x-api-key`, `x-goog-api-key`, cookies and client IP headers are stripped before forwarding.

## Rate limiting — per-isolate, not a global quota

`RATE_PER_MINUTE` (default 120) is enforced by an in-memory fixed window **inside each Cloudflare isolate**. Cloudflare runs many isolates, so the same device token can exceed 120 requests/minute in total when its requests land on different isolates. Treat the limiter as a brake on a runaway agent loop on one device, not as a hard global quota: providers enforce their own limits behind it, and the app must not assume the Worker caps total usage.

## Request log — count-only

Every request writes one JSON line: `{"type":"helium_request","method":"GET","path":"/v1/tools/search","status":200,"ms":123}`. That is all it ever contains — no query string, no headers, no body, no device token, no provider key. It is **on by default**; set `REQUEST_LOG=0` in `[vars]` to turn it off (`REQUEST_LOG=1` turns it back on).

## Deploy

```sh
cd cloud/worker
npm install
npx wrangler login
npx wrangler secret put HELIUM_TOKENS      # e.g. output of: openssl rand -hex 24
npx wrangler secret put DEEPSEEK_API_KEY   # optional: enables DeepSeek search + /llm/deepseek
npx wrangler secret put OPENCODE_API_KEY   # optional: /llm/opencode-go
npx wrangler deploy
```

Then in the app: 模型与推理 → Helium Cloud → Worker URL + one device token. With Cloud connected, Exa search goes through `/mcp/exa`, DeepSeek search through `/v1/tools/search`, and a provider with no key on the phone goes through `/llm/<provider>`.

## Test

```sh
npm test   # node --experimental-strip-types --test, no install needed
```
