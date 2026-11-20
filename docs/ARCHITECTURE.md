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

## Scheduled tasks (M5)

```
TasksScreen / task_* tools ──► TaskEngine (TaskService) ──► Room scheduled_tasks + task_runs
                                    │
             arm(nextRunAt) ────────┤  TaskScheduler
                                    ├─► AlarmManager  setExactAndAllowWhileIdle (SCHEDULE_EXACT_ALARM) │ setAndAllowWhileIdle fallback
                                    └─► WorkManager backup (+5 min)            ─┐
TaskAlarmReceiver / TaskWorker(trigger) ──► TaskEngine.trigger(): CAS claim on nextRunAt (one winner) → arm next
        NOTIFY (reminder) → notification right there        AGENT → WorkManager TaskWorker(run, expedited)
TaskWorker(run) ──► TaskEngine.execute(): headless AgentController run into the task's own conversation
                    BackgroundApprovalGate: PENDING approval row + 批准/拒绝 notification, 5 min silence = deny
                    → task_runs row (cause, attempt, startedAt…finishedAt, summary | error) → result notification
                    failure → Backoff 1, 2, 4, 8, 16 min (retry work requires network) → then wait for next fire
BootReceiver (BOOT_COMPLETED, package replaced, time / zone change, exact-alarm grant) ──► rearmAll():
                    overdue ≤ 2 h → run now (补跑); older → recorded as 错过, move on; RUNNING rows of a dead process → FAILED
```

| 文件 | 内容 |
|---|---|
| `tasks/Schedule.kt` | 纯逻辑：`Schedule.nextFire`（本地时区、每天/每周/一次）、`Backoff`、`FailureReason` |
| `tasks/TaskArgs.kt` | 工具参数解析（时间 "8:00/8点半/下午3点"、星期 [1,3,5]/"工作日"/"周一到周五"、date/in_minutes） |
| `tasks/TaskTools.kt` | `task_create`(中) `reminder_create`(低) `task_list`(低) `task_update`(删除=高，修改/立即运行/恢复=中，暂停/跳过=低) |
| `agent/tools/DeviceTools.kt` | `calendar_read`(低，READ_CALENDAR 运行时权限) `notify`(低，POST_NOTIFICATIONS) |
| `tasks/TaskEngine.kt` | CRUD、触发认领、无头运行、运行历史、退避重试、开机重排 |
| `tasks/TaskScheduler.kt` · `TaskWorker.kt` · `Receivers.kt` · `Notifications.kt` · `PermissionBroker.kt` | Android 外壳 |

Each run's events are the slice of the task conversation between `task_runs.startedAt` and `finishedAt`; the 任务详情 page opens it in the normal timeline.
