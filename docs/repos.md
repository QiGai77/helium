# Helium · 5 个借鉴仓库对比

> 调研日期：2026-11-19（Asia/Shanghai）。star、许可证、结构均取自当天打开的 GitHub 页面/官方文档；未打开的仓库不写事实。

## 入选 5 仓库总表

| # | 仓库 | URL | Star（当日） | 许可证 | 定位 | 对 Helium 的价值 | 可否直接用代码 |
|---|---|---|---|---|---|---|---|
| 1 | **OpenFuse**（原 CopilotKit/OpenMuse） | https://github.com/CopilotKit/openfuse （旧地址 CopilotKit/openmuse 跳转至此） | 6.5k | MIT | 手机优先的个人 agent 模板：RN 客户端 + Hono API + 持久任务 worker + 浏览器 worker + 可选 Linux 容器 | **产品骨架最接近**：聊天/Activity/Ideas/Goals&Tracking/Docs、存储式"动作审阅"、AG-UI 流式事件、Take control | 可（MIT），但 Rich Threads 依赖闭源 CopilotKit Intelligence，需替换 |
| 2 | **nanoMuse**（原 OpenMuseAgent/OpenMuse） | https://github.com/nano-muse/nanoMuse | 3.6k | GPL-3.0-or-later（0.x Python 线早期 MIT，tag `pre-openminis`） | 跨设备个人 agent：Android/iOS/桌面/Web + 自建 relay；手机 GUI "Hands" | **审批/安全模型最好**：Sentinel 闸门、风险分级、grant key、作用域授权、taint 防外泄、vault 占位符、审计；记忆可读可编辑 | **只借思想**（GPL 传染，闭源 App 不能拷代码） |
| 3 | **browser-use** | https://github.com/browser-use/browser-use | 121.5k | MIT | Python 浏览器 agent：模型在持久 REPL 里写 Python，驱动 CDP | 浏览器代理执行层：持久 profile、`sensitive_data` 秘密注入、域名白名单、Judge 自评、结构化输出 | 可（MIT），需钉死版本 `==` |
| 4 | **mem0** | https://github.com/mem0ai/mem0 | 69.2k | Apache-2.0 | Agent 记忆层：库/自托管服务/云 | 长期记忆：User/Session/Agent 多级记忆；语义+BM25+实体融合检索；时间推理；ADD-only 抽取 | 可（Apache-2.0，保留 NOTICE） |
| 5 | **Mobilerun**（原 droidrun） | https://github.com/droidrun/mobilerun （droidrun/droidrun 跳转至此） | 10.1k | MIT | LLM 无关的手机 agent 框架：Android Portal(无障碍服务) + iOS | 手机操作代理：Manager/Executor/Fast 多 agent、a11y 树+截图、`type_secret`、App Cards、轨迹追踪 | 可（MIT） |

## 逐仓库记录

### 1. OpenFuse（CopilotKit，原 OpenMuse）— MIT · 6.5k★
- **核心架构**（README Architecture 图）：Expo/React Native/Web 客户端 →（AG-UI + 鉴权 API）→ Hono + CopilotKit runtime → 持久任务 worker；存储 PGlite 或 PostgreSQL；任务 → "Stored action review" → Gmail/Calendar 适配器；Chromium worker（持久 profile）；可选 Docker Linux 计算机（持久 workspace 卷）。
- **目录**：`apps/mobile`（iOS/Android/Web 共用 UI，CopilotKit headless hooks）、`apps/server`（API、runtime、身份边界、任务引擎、reviews、文件、持久化）、`apps/worker`（带 token 的 Playwright 浏览器服务）、`apps/computer`（非 root Linux 镜像）、`packages/domain`（共享类型与校验）、`packages/integrations`（Google 与浏览器协议适配器）。
- **Agent loop / 工具**：CopilotKit runtime + AG-UI 事件流；网页搜索走 Parallel 的 Search MCP；委派任务有计划、进度、输入请求、暂停/恢复/取消/重试、审批与回执；**SQL 租约（leases）恢复被中断的任务**。
- **审批**：每次发送邮件/改日历都要一条"stored review"；"不确定的外部写入后不做隐式重试"。
- **记忆**：可编辑的名字、语气、头像与 memories；Ideas 带来源证据，可接受/编辑/忽略。
- **定时**：Goals & Tracking —— 周期性公开网页检查（变化、可用性、USD 价格阈值），去重告警、失败退避。
- **多端**：一套 RN 代码出 iOS/Android/Web；通知为应用内收件箱，APNs/FCM 推送在 roadmap。
- **值得借鉴**：`apps/server` 的任务引擎 + 租约恢复；"动作审阅"存储化（审批是一条可追溯记录，不是一次性弹窗）；浏览器 "Take control" 接管；聊天内联富卡片（邮件、浏览器、PDF、计划、财务）；send/stop 同一输入胶囊、保留草稿、可见的追问队列。
- **不适合**：Alpha；单 owner + 共享 access key，非多租户；Rich Threads 必须 CopilotKit Intelligence（不在 MIT 范围、需项目 key、默认遥测到 PostHog）；终端容器无网络、30 秒命令上限；无自主下单/支付；RN 难做原生 M3 Expressive。
- 来源：https://github.com/CopilotKit/openmuse · https://github.com/CopilotKit/openfuse/blob/main/docs/FEATURES.md

### 2. nanoMuse（原 OpenMuse by OpenMuseAgent）— GPL-3.0-or-later · 3.6k★
- **核心架构**（docs/architecture.md）：`MuseAgent.run()` 每轮构建系统提示（档案、语言规则、相关记忆、活跃目标）→ 循环：调模型 → 每个工具调用交给 `Sentinel.guard()` → 追加结果 → 模型调用 `terminate` 或无工具回复时停止；轮中新消息并入下一次调用；重复调用无进展触发卡死检测；`max_steps` 强制收尾。
- **关键文件**：`nanomuse/agent/core.py`（loop）、`nanomuse/sentinel/gate.py|policy.py|audit.py|review.py`、`nanomuse/vault/vault.py`（Fernet，`{{vault:NAME}}`）、`nanomuse/tools/base.py`（`BaseTool`、`CallAssessment`）、`nanomuse/tools/mcp_tools.py`（stdio/HTTP/SSE MCP，每工具风险覆盖）、`nanomuse/phone/operator.py`（手机操作循环，999 网格坐标）、`nanomuse/memory/store.py|consolidate.py`（SQLite，关键词+语义检索，整理与撤销日志）、`nanomuse/server/outbox.py`（定时任务至少一次投递）、`nanomuse/ui.py::UI` 协议。
- **审批（Sentinel）**：工具静态风险 safe/moderate/sensitive，`assess()` 可按参数升级（如 `phone_act` 点击标签含"确认支付/转账/提交订单/发送/删除"即升为 sensitive）；模式 ask/strict/auto；**授权作用域**：拒绝 / 仅一次 / 本对话 / 至重启 / 24 小时 / 永久；**grant key 绑定目标**（如 `send_email:alice@…`、`browser:booking.com`）；读取私密数据后对白名单外主机的外发一律要问（taint）；审批卡展示工具、摘要、目的、精确参数、风险与原因；未答 1 小时视为拒绝；审计 `audit.jsonl`。
- **多端**：Android（基于 OpenMinis，proot+Alpine）、iPhone/iPad、桌面、Web；经 relay 互相派活，审批回到手边设备。
- **值得借鉴**：整套 Sentinel 决策顺序与审批作用域（直接映射为 Helium 的审批卡）；taint 防 prompt injection；记忆/身份/作息为可读可改的 Markdown；Permissions 页可逐条撤销授权。
- **不适合**：GPL-3.0 —— 闭源商业 App 不能复制代码；Sentinel 与 agent 同进程、非 bubblewrap 环境下 shell 以用户身份运行（sentinel.md 自述）；屏幕操作在 iOS 上不可用（README 自述系统不允许）；默认社区 relay 由开发者付费，商业化不可依赖。
- 来源：https://github.com/nano-muse/nanoMuse · https://github.com/nano-muse/nanoMuse/blob/main/docs/sentinel.md · https://github.com/nano-muse/nanoMuse/blob/main/docs/architecture.md

### 3. browser-use — MIT · 121.5k★
- **核心架构**：1.0 版：模型在**一个持久 Python REPL** 中写代码，调用 browser-harness 助手与原生 CDP；跑在 Pydantic AI 上；"The model, a REPL and CDP. That's the whole agent."
- **浏览器形态**：`Browser()` 云浏览器；`Browser('chromium')` 临时本地；`Browser('chrome')` 接管已开 Chrome；`user_data_dir` / `cloud_profile_id` 保持登录。
- **值得借鉴**：`sensitive_data` + `type_secret(name)`（输出中替换为 `<secret:name>`）；`allowed_domains`/`prohibited_domains`/`block_ip_addresses`；`output_model_schema` 结构化结果；`@tools.action` 自定义工具；**Judge**：跑完再用同模型评判，返回 success、failure_reason、prompt_injection_detected、bot_block_or_captcha_detected、gotchas（站点经验，可回写成技能）；highlight 操作框；`mode='ultrafast'` 用 a11y id；live view 链接。
- **不适合**：README 自述 secrets"不是沙盒"（模型写的 Python 可读到值）、域名限制"不是沙盒"；默认走 Browser Use Cloud（计费、遥测）；发布快，必须 `==` 钉版本；本地 CDP 端口无鉴权，须容器化。
- 来源：https://github.com/browser-use/browser-use

### 4. mem0 — Apache-2.0 · 69.2k★
- **核心架构**：`mem0/` 包下 `memory/`、`llms/`、`embeddings/`、`vector_stores/`、`reranker/`、`client/`、`proxy/`、`configs/`；另有 `server/`（自托管，docker compose，默认开启鉴权）、`mem0-ts/`、`cli/`。
- **算法（2026-04 新版）**：单次 ADD-only 抽取（不 UPDATE/DELETE，记忆累积）；agent 产生的事实同权存储；实体抽取+链接；语义+BM25+实体并行打分融合；时间感知检索。README 标注 benchmark 分数来自托管平台、含开源 SDK 没有的优化。
- **值得借鉴**：`search(query, filters={"user_id"...})` → 注入系统提示 → 回复后 `add(messages)` 的最小闭环；User/Session/Agent 三级记忆；自托管 server 直接作为 Helium 记忆服务。
- **不适合**：ADD-only 意味着"用户改口/要求忘记"需要我们自己做失效与删除层；默认依赖 OpenAI 模型与 embedding；没有面向用户的可编辑记忆 UI。
- 来源：https://github.com/mem0ai/mem0 · https://github.com/mem0ai/mem0/tree/main/mem0

### 5. Mobilerun（原 droidrun）— MIT · 10.1k★
- **核心架构**（docs.mobilerun.ai Architecture）：`MobileAgent` 编排；Reasoning 模式 `ManagerAgent`（规划子目标）→ `ExecutorAgent`（原子动作）循环；Direct 模式 `FastAgent`（XML 工具调用）；共享 `MobileAgentState`（动作历史、错误、记忆、计划进度）；可给每个角色配不同 LLM。
- **文件**：`mobilerun/agent/droid/droid_agent.py`、`agent/manager/manager_agent.py`、`agent/executor/executor_agent.py`、`agent/fast_agent/fast_agent.py`。
- **动作集**：`click(index)`、`click_at(x,y)`、`long_press`、`type`、`type_secret(secret_id,index)`、`swipe`、`system_button`、`open_app`、`complete(success,message)`。
- **设备端**：Android 用 Portal App（启用无障碍服务，提供 UI 树、截图、手势）；iOS 真机经 USB 的 `mobilerun-ios`，模拟器用 iOS Portal。
- **值得借鉴**：Portal = 我们 Android App 内的 AccessibilityService 模块；a11y 树优先、截图兜底（`--vision-only` 用于无 a11y 的 App）；App Instruction Cards ≈ Helium 的"技能/专长"；Phoenix/Langfuse 追踪与轨迹回放。
- **不适合**：框架默认在电脑上通过 adb 驱动手机，不是手机内自驱；iOS 真机需 USB 连电脑（系统限制）；面向 QA/自动化，缺少个人助理层（记忆、审批、定时）。
- 来源：https://github.com/droidrun/droidrun · https://docs.mobilerun.ai/framework/concepts/architecture

## 看过但未入选

| 仓库 | URL | Star | 许可证 | 未入选原因 |
|---|---|---|---|---|
| OpenHands（现为 Agent Canvas） | https://github.com/OpenHands/OpenHands | 93.8k | MIT | 定位是编码 agent 控制台；但其 **Agent Server（software-agent-sdk）+ Automation Server（定时/Webhook 派发）** 的拆分、每会话独立 Docker 沙盒，可作为 v2 "云电脑/沙盒"参考 |
| Letta（MemGPT） | https://github.com/letta-ai/letta | 25.5k | Apache-2.0 | 本仓库已转为指向 `letta-ai/letta-code`，V1 服务端在 archive 分支；理念（自管理记忆块）可参考，但工程上 mem0 更易嵌入 |
| Khoj | https://github.com/khoj-ai/khoj | 38.2k | AGPL-3.0 | 个人"第二大脑"+定时自动化+多端（含手机/WhatsApp），但 AGPL 对 SaaS 后端风险高，且偏检索问答而非代办执行 |

未打开、因此不作结论：Open Interpreter、LibreChat、Open WebUI、Leon、AppAgent、Mobile-Agent。
