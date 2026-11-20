# Helium 端上 Agent 架构（v0.2 地基）

```
UI (Compose)  ──  ChatViewModel ──► AgentController ──► ModelRunner (LlmClient: OpenAI / Anthropic / Gemini tools)
                                         │
                                         ├─► Sentinel.guard()  ─ deny → high risk → tainted exfil → grant → risk×mode
                                         │        └─ Ask ─► ApprovalBroker (Room "approvals" + CompletableDeferred) ─► 审批卡
                                         ├─► ToolRegistry  (get_current_time · web_fetch · memory_search/save/forget)
                                         └─► EventSink / AuditSink (Room)
```

| 层 | 文件 | 借鉴 |
|---|---|---|
| 事件流 | `agent/Events.kt` — 用户消息、模型回合、工具观察、审批请求/决定，全部追加写入 | OpenHands EventStream（Action / Observation） |
| Agent 循环 | `agent/AgentController.kt` — 步数上限、停止、超时、卡死检测 | OpenHands AgentController + StuckDetector |
| 上下文投影 | `agent/History.kt` — 每个工具调用配一个结果；外部内容加围栏 | OpenHands condenser 思路；taint 来自 nanoMuse 文档（clean-room） |
| 安全闸门 | `agent/Sentinel.kt` — 风险分级、按「工具+目标」授权、作用域（一次/本对话/24h/永久） | nanoMuse Sentinel 设计（只借思想，GPL 代码不复制） |
| 审批记录 | `data/db/Repositories.kt` `ApprovalBroker` — 审批是持久记录，进程重启后过期 | OpenFuse stored action review |
| 存储 | `data/db/*` — Room：conversations / events / memories / forget_rules / grants / audit / approvals / scheduled_tasks | — |
| 工具协议 | `data/llm/WireFormat.kt` + `LlmClient.kt` — 三种协议的 function calling 与流式拼装 | — |

新增工具：实现 `agent/tools/Tool.kt` 的 `Tool`（声明 `risk(args)`、`target(args)`、`title/detail`），在 `HeliumApplication.tools` 注册即可；高风险工具自动走审批。


## Search and Helium Cloud

- `web_search` is an ordinary function tool run on the phone. DeepSeek's server-side `deepseek_web_search` cannot be declared together with function tools yet, so the agent keeps search client-side, like opencode.
- Backends (`agent/search/Search.kt`): **Exa public MCP** (default, free, keyless, JSON-RPC `tools/call web_search_exa`, the same backend opencode uses) and **DeepSeek Search API** (`/v1/tools/search`, $0.75 per 1,000 successful requests). A failure on the chosen backend falls back to Exa and says so in the observation.
- Results are rendered as numbered sources; the model cites `[n]`, and the chat shows the turn's sources as chips under the answer (chat-demo style).
- `cloud/worker` is Helium Cloud: a stateless Cloudflare Worker that injects server-held keys (DeepSeek, OpenCode Go, Gemini) and relays Exa. The app uses it when a Worker URL + device token are set; a key on the phone always wins for model calls.


## Chat (M1)

- **Storage.** A conversation is still one append-only event stream (`events`), so the chat, model context, timeline and audit stay projections of one source. DB v2 (`MIGRATION_1_2`, SQL in `data/db/ChatSchema.kt`) adds `conversations.titleState` (provisional / auto / user), `events.searchText`, the external-content FTS4 table `event_fts` with Room's sync triggers, and `pending_replies` (text streamed so far for the reply in flight). `ChatSchemaTest` checks the migration SQL against Room's exported `app/schemas/.../2.json` in CI.
- **Search.** FTS4's `simple` tokenizer keeps a CJK run as one token, so `searchText` stores CJK one character per token and `FtsQuery` turns input into per-character phrases plus prefix words (RikkaHub's per-character idea, done with FTS4 phrases). Hits open the conversation scrolled to the message.
- **Titles.** After the first finished exchange the active model is asked for a ≤12-character title (`TitlePrompt`, shape from RikkaHub's TitleSummary); a user rename is never overwritten.
- **Interrupted turns.** Partial text is saved to `pending_replies` about once a second. On open, an unfinished tail (user message, tool step, approval) gets an "已中断" assistant event carrying that text, with 重试 (`Recovery.interruptedTail`).
- **Images.** Camera (`TakePicture` + FileProvider) or Photo Picker → EXIF-rotated, longest edge ≤1568 px, JPEG in `files/attachments/`; events store the file name. `WireFormat` inlines them per protocol: OpenAI `image_url` data URI, Anthropic `image` base64 block, Gemini `inlineData`. Only the 3 most recent image turns keep pixels in context.
- **Voice.** Hold the mic: system `SpeechRecognizer`, zh-CN, partial results shown live, final text appended to the draft (RECORD_AUDIO asked on first use).
- **Offline.** Send checks connectivity first; offline keeps draft and attachments and shows a banner with 重试. A failure mid-request keeps the user message and shows the error card with 重试.
