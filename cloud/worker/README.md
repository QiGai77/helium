# Helium Cloud (Cloudflare Worker)

A stateless proxy between the Helium app and third-party APIs. It holds provider keys as Worker secrets so they never sit on the phone, and gives the app one reachable origin on your own domain.

| Route | What it does |
| --- | --- |
| `GET /health` | Public. Which providers are configured (never the keys). |
| `POST /v1/tools/search` · `/v1/tools/fetch` | DeepSeek Search / Fetch API, same body and response as `api.deepseek.com`. |
| `POST /mcp/exa` | Exa's public MCP endpoint (keyless; `EXA_API_KEY` optional). |
| `ANY /llm/<provider>/<path>` | Model APIs with the key injected: `deepseek`, `opencode-go`, `gemini`. Streams SSE through. |

Every route except `/health` needs `Authorization: Bearer <device token>`; tokens are the comma-separated `HELIUM_TOKENS` secret. The device's own `Authorization`, `x-api-key`, `x-goog-api-key`, cookies and client IP headers are stripped before forwarding. A per-isolate limiter (`RATE_PER_MINUTE`, default 120) brakes a runaway agent loop.

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
