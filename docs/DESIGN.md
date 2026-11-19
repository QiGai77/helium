# Helium · 设计语言 v0.1

> 个人 AI agent 手机 App。聊天、长期记忆、技能、代理操作手机/浏览器、定时任务、Home 面板、购买前审批。
> 视觉基调：Google 当代设计语言（M3 Expressive / Gemini / Antigravity / Pixel / I/O）的"轻、亮、有弹性"，但用自己的色彩故事和组件，不用任何 Google 商标或 logo。
> 日期：2026-11-19 · 状态：概念方向（AI 生成概念稿，非可交付像素稿）

---

## 0. 交付物

| 文件 | 说明 |
|---|---|
| `design/01-home-bento.png` | Home：问候语 + bento 渐变卡（航班 18:40 / 预算 / 天气）+ 包裹钱包叠卡 + agent 光球 + 发光输入胶囊 |
| `design/02-chat-live-agent.png` | 对话：顶部"思考辉光"，用户深色气泡，Agent 实时操作卡（浏览器预览 + 步骤勾选 + 波浪进度 + 接管/暂停） |
| `design/03-task-timeline.png` | 任务时间线：渐变进度条 4/6，形状节点（圆/饼干/方）串联步骤卡，当前步骤丁香渐变、待审批琥珀描边 |
| `design/04-approval-card.png` | 审批：暖色 mesh 渐变审批卡 + 钱包叠卡 + 金额明细 + 深色"批准并支付"主按钮 + 安全说明 |
| `design/05-memory-skills.png` | 记忆与技能：分段切换，记忆胶囊气泡，技能 bento（M3 形状图标 + 开关） |
| `design/06-schedule-projects.png` | 定时与项目：周历胶囊，定时任务列表，项目钱包叠卡（环形进度 65%） |
| `design/helium-overview-board.png` | 6 屏总览 board（4864×2280，含色板与字体说明） |
| `design/drafts/*-v1.png` | 被淘汰的第一版（问题：饼干 emoji、缩略图乱码、状态栏写成 "9:19.5"、英文标签泄漏、卡片标题被遮挡） |

单屏尺寸 704×1520（≈9:19.4）。`03` 在第二版中有一处模型把色值 "#FFC53D" 写成了文字，已用 PIL 擦除并替换为琥珀状态点。

---

## 1. 品牌概念：氦 Helium

- **轻**：信息密度低、留白大、卡片像被托起（柔和彩色投影，不用硬黑影）。
- **浮 / 反重力**：背景有缓慢上浮的彩色微粒与模糊光球；元素出场方向**自下而上**，带一点过冲回弹（像气球松手）。
- **惰性**：Agent 默认安静、不打扰；只有在"正在做事"时才发光。发光 = 工作中，熄灭 = 闲置。
- **冷光**：氦在放电管里发出的特征谱线。我们直接把**氦发射光谱的谱线**当作品牌色来源——这是 Helium 独有的色彩故事，而不是借用 Google 四色。

---

## 2. 色板

### 2.1 谱线色（品牌强调色，取自氦发射线波长的近似色）
| Token | Hex | 来源 | 用途 |
|---|---|---|---|
| `he-447` 蓝 | `#4F6BFF` | 447 nm | 主色、主行动、进度起点 |
| `he-492` 青 | `#19C6E6` | 492 nm | Agent 思考辉光、链接 |
| `he-501` 薄荷 | `#2FD6A0` | 501 nm | 成功、完成、健康类面板 |
| `he-587` 琥珀 | `#FFC53D` | 587.6 nm（He D3 线） | 待审批、提醒、金额 |
| `he-668` 珊瑚 | `#FF6B6B` | 667.8 nm | 警示、购物/消费 |
| `glow` 丁香 | `#B9A7FF` | 辉光混色 | 当前步骤、记忆、柔和高光 |
| `blush` 粉 | `#FF9ECF` | 辉光混色 | 审批卡尾色、生活类 |

### 2.2 中性色
| Token | Hex | 用途 |
|---|---|---|
| `ink` | `#12131A` | 主文字、主按钮（深色胶囊）、用户气泡 |
| `ink-2` | `#5A5F73` | 次级文字 |
| `ink-3` | `#9AA0B4` | 占位、禁用 |
| `canvas` | `#F4F5FB` | 页面底（冷白，带一点蓝） |
| `surface` | `#FFFFFF` | 卡片 |
| `mist` | `#E9ECF7` | 分割、次级胶囊底 |

### 2.3 Mesh 渐变配方（饱和卡面，满足"饱和 mesh / 白底浅渐变"的偏好）
| 名称 | 色点 | 场景 |
|---|---|---|
| **Aurora** | `#4F6BFF` → `#B9A7FF` → `#19C6E6` | 航班、主项目、思考辉光 |
| **Sunrise** | `#FFC53D` → `#FF6B6B` → `#FF9ECF` | 审批、预算、购物 |
| **Lagoon** | `#2FD6A0` → `#19C6E6` | 天气、健康、完成态 |
| **Lilac** | `#B9A7FF` → `#FF9ECF` | 记忆、邮件、当前步骤 |

规则：
- 一屏最多 **2 种**饱和 mesh 卡做主角，其余为白卡或浅渐变胶囊；避免"全屏彩虹"。
- 饱和卡上文字一律白色 ≥ 20sp / Medium，或深色 `ink`（Sunrise 卡上标题用 `ink` 对比更好，见 04）。
- 不做全暗线框风；暗色模式另立（canvas `#0E0F16`、卡面提亮 8%、谱线色保持饱和）。
- 渐变借鉴 Gemini 的"前沿实、尾部散"：发光边缘在行动方向一侧更亮。

---

## 3. 字体

| 角色 | 推荐 | 许可 / 可用性 |
|---|---|---|
| 拉丁 / 数字 / 品牌词 | **Google Sans Flex**（可变：wght 1–1000、wdth 25–151、opsz 1–144、slnt、GRAD、ROND 0–100） | SIL OFL 1.1，无保留字体名（googlefonts/googlesans-flex 仓库；2025 年开源）。仓库另有 TRADEMARKS.txt，**不要**在产品里用 "Google" 名义宣传字体关系。 |
| 中文 | **Noto Sans SC**（可变 wght）；备选 MiSans / HarmonyOS Sans（需各自核对商用条款） | Noto Sans SC 为 OFL。Google Sans Flex 的中文覆盖未核实，中文默认走 Noto Sans SC。 |
| 等宽（日志/代码/步骤详情） | Google Sans Code 或 JetBrains Mono | 均为 OFL |
| 无 Flex 时的替代 | Inter（中性、工具感）/ Outfit（几何、更圆、更接近 Google Sans 气质） | 均为 OFL |

用法：
- **ROND 轴是 Helium 的签名**：大标题与数字 ROND 60–100（圆润、轻），正文 ROND 0–20（清晰）。
- 大数字（18:40、¥1,299、65%）用 wght 500 + opsz 96+，tabular 数字。
- 中文标题 Bold 700，正文 Regular 400；中英混排时拉丁字号放大约 4–6% 以对齐中文视觉高度。
- 字号阶梯（sp）：Display 44 / Headline 32 / Title 22 / Body 16 / Label 13 / Caption 12。

---

## 4. 形状

- 圆角 token（对齐 M3 Expressive）：`sm 12` · `md 16` · `lg 20` · `xl 32` · `xxl 48` · `full`（胶囊）。
  - 卡片 28–32；大面板/审批卡 32；底部 sheet 48；按钮、输入框、chip 一律 `full`。
- **形状语义（Helium 自定）**
  - 圆 ● = 完成 / 静止
  - 12 瓣扇贝"花" ✿ = Agent 本体（光球、头像、思考中）
  - 软方 ▢ = 正在执行
  - 空心圈 ○ = 等待你
  - 灰方 = 未来步骤
- **Shape morphing**：Agent 光球在 圆 → 扇贝花（开始思考）→ 软方（执行工具）→ 圆（完成）之间变形；时间线节点完成时从软方 morph 成圆并弹一下。
- **张力**：整体是圆的，唯一的直角/锐角留给"需要你决定"的元素（例如审批卡左上角的 `待审批` 标签用 8px 小圆角），制造注意力。

---

## 5. 动效原则

基于弹簧（stiffness / damping），不用固定时长曲线。

| Token | 用途 | 建议值（Compose `spring`） |
|---|---|---|
| `float.spatial` | 卡片出场、叠卡展开、光球位移 | dampingRatio 0.75，stiffness 380（有轻微过冲） |
| `float.fast` | 按钮、开关、chip | dampingRatio 0.8，stiffness 1400 |
| `glow.effects` | 颜色、透明度、辉光 | dampingRatio 1.0（无过冲），stiffness 200 |

1. **上浮**：新内容自下而上 12–24dp 位移 + 淡入，像气体上升。
2. **思考 = 发光**：Agent 工作时，屏幕顶部 Aurora 辉光缓慢呼吸（4s 周期），停止即熄灭；不用转圈 spinner。
3. **叠卡物理**：钱包叠卡拖拽时像磁铁展开，松手弹回（参考 Figma Riffs 的 "Magnetic Player"、"deck of cards"）。
4. **审批是英雄时刻**：一屏只给它最强动效——卡片从底部浮起、金额数字滚动落定、批准后卡片向上飘走并化为薄荷色勾。
5. **克制**：每个流程最多 1–2 个英雄时刻；其余用 `fast` 弹簧。
6. **可打断**：所有 Agent 动画允许用户随时点"接管/暂停"，动画立刻 retarget，不等播完。

---

## 6. 核心组件

### 6.1 输入胶囊 Composer
- 浮在底部 16dp、`full` 圆角、白底；**边框是 2dp 的 Aurora→Sunrise 渐变描边**，空闲时 30% 透明，聆听/思考时 100% 并缓慢流动。
- 左：`+`（附件与技能分开，不要都塞进 "+"——Gemini 新版因此被批评过）；右：麦克风深色圆按钮。
- 状态：空 / 输入中 / 语音（胶囊内波形）/ Agent 运行中（变为"继续说…"+ 停止）。

### 6.2 Agent 实时操作卡 Live Run Card
- 白卡 28dp，**发光渐变描边** = 正在运行。
- 头部：脉动光点 + 动词短句（"正在操作浏览器"）。
- 实时缩略图：被操作页面的低清预览（可点开全屏观看）。
- 步骤列表：✓ 已完成 / 波浪进度 进行中 / ○ 待做；每步可展开看细节。
- 操作：`接管`（浅色胶囊）、`暂停`（描边胶囊）。

### 6.3 任务时间线 Run Timeline
- 顶部总进度条（Aurora→Lagoon）+ "已完成 4/6"。
- 左侧渐变线串联形状节点（见 §4 形状语义），右侧步骤卡带时间戳。
- 当前步骤：Lilac 渐变卡 + 星点闪烁；待审批：琥珀描边 + 琥珀状态点。

### 6.4 审批卡 Approval Card
- 上半 Sunrise mesh，`待审批` 标签 + 标题（ink 色更易读）；下半白底明细：商品 / 运费 / **合计（最大字）** / 付款卡尾号 / 送至。
- 后面 1–2 张浅色卡露边，暗示"还有其他待办"。
- 主按钮深色 `ink` 胶囊"批准并支付"，次按钮描边"拒绝"；底部固定一句安全承诺："未经你同意，Helium 不会付款"。
- 规则：金额、收货地、付款方式必须同屏可见，不可折叠。

### 6.5 Home 面板 Bento
- 2 列网格，单元 1×1、2×1；卡片高度按 4 的倍数。
- 至多 2 张饱和 mesh 主卡，其余白卡/浅渐变；数字用大号 Flex ROND。
- 物流、票据类用**钱包叠卡**（3 张露边）。
- 右下角 Agent 光球（扇贝花形）= 进入对话的快捷入口。

### 6.6 技能卡 Skill Card
- 1×1 bento，各自专属 mesh；左上为 M3 风格形状底（扇贝 / 花 / 胶囊 / 软方）+ 白色线性图标；左下技能名；右下开关。
- 记忆用彩色胶囊气泡（浅渐变底 + ink 文字），可长按编辑/删除。

---

## 7. 参考清单（均已打开验证；仅借鉴风格，不复制作品、不使用 Google 商标/logo）

| # | 标题 | URL | 作者 / 来源 | 许可 | 可借鉴点 |
|---|---|---|---|---|---|
| 1 | Start building with Material 3 Expressive | https://m3.material.io/blog/building-with-m3-expressive | Material Design（Google） | 官方指南，页面未声明再利用许可 | 7 条表现力策略：多样形状、丰富色彩、强调字体、容器强调、流体动效、组件弹性、"英雄时刻"（每个产品只留 1–2 个）；35 个形状 + 形状变形 |
| 2 | Shape – Material Design 3 | https://m3.material.io/styles/shape/overview-principles | Material Design | 同上 | 圆角 token：L 20 / XL 32 / XXL 48 / full；形状变形用于状态、进行中、环境变化；圆+方制造张力；形状不要赋予固定语义；2.5D 层次 |
| 3 | Motion physics system | https://m3.material.io/styles/motion/overview/how-it-works | Material Design | 同上 | 弹簧取代时长曲线；expressive（过冲）vs standard；spatial（位移可过冲）vs effects（颜色/透明度不过冲）；fast/default/slow 三档 |
| 4 | Illustrating the Gemini App | https://design.google/library/gemini-ai-visual-design | Google Design | 文章，未声明许可 | 渐变 = 能量与方向，"前沿实、尾部散"；以圆为基础形状；Material 形状柔化模糊成"空灵"质感；动效有起点终点、内部活动表示思考；雷达式波纹表示语音 |
| 5 | Making Google Sans Flex | https://design.google/library/google-sans-flex-font | Google Design | 文章 | 6 轴可变字体；ROND 圆度轴带来"个人、俏皮"语气；opsz 保证小字可读；2025 年开源 |
| 6 | googlefonts/googlesans-flex | https://github.com/googlefonts/googlesans-flex | Google Fonts | **SIL OFL 1.1**（无保留字体名；另有 TRADEMARKS.txt） | 轴范围：wght 1–1000、wdth 25–151、opsz 1–144、GRAD 0–100、ROND 0–100 → 直接用于 Helium 字体规范 |
| 7 | Google Antigravity（官网） | https://antigravity.google/ | Google | 产品官网，商标归 Google | "liftoff/反重力"叙事、大留白、光标图形母题、按产品分卡片的结构；App 端"Projects + 定时消息"的信息架构可对照 Helium 的项目/定时 |
| 8 | Google Antigravity · Websites on Display | https://ondisplay.website/site/antigravity | Websites on Display（第三方归档） | 截图版权归原作者 | 被归纳为"空气感极简 + 彩色微粒 + 高对比字体"；字体 Google Sans Flex / Code；取色 #FEFEFF #1B1C1F #DDE0E4 #8F9CB1 #595B5E #CB7D4D → Helium 背景微粒与 ink 的出处 |
| 9 | I tried Gemini's redesigned Android app（Neural Expressive 实测） | https://www.androidauthority.com/gemini-neural-expressive-android-app-hands-on-3668985/ | Brady Snyder / Android Authority | 新闻评测 | 浮动胶囊输入框、思考时上半屏渐变辉光、Live 收成底部小胶囊；**反面教训**：工具与附件都塞进"+"导致难发现 → Helium 拆开 |
| 10 | How we used Gemini to build Google I/O 2026 | https://blog.google/innovation-and-ai/technology/ai/io-2026-google-ai/ | Marvin Chow / Google Keyword | 博客 | I/O 2026 视觉：四色渐变 + 叠加透明度 + 互锁图标；2D 扁平图标转为高质感 3D → Helium 技能图标可做 2D→3D 的 hover/选中态 |
| 11 | Riffs by the Figma Community | https://www.figma.com/community/feed | Figma 社区（Jorge Molina、Hijrat、Alejandro Vasquez、Crafttor 等） | 各 riff 未标许可，视为版权保留 | 动效灵感：卡组顶牌飞出其余前移（Alejandro Vasquez）；卡堆像磁铁展开（Hijrat "Magnetic Player"）；卡片圆角 20→60 变形的环绕动画（Hijrat）；弹簧拉伸的胶囊 tab（Crafttor）；支付按钮变收据打印（Jorge Molina）→ 审批成功动效 |
| 12 | AI Agent UI Kit – Reasoning, Tool Use & Response Components | https://www.figma.com/community/file/1654386819853232641 | Viacheslav Derzhaiev | **CC BY 4.0**（需署名） | 组件清单：Composer 6 态、状态（思考/生成/使用工具/搜索）、推理折叠、工具调用卡（运行/完成）、来源 chip → Helium 组件状态表的对照 |
| 13 | Material 3 Design Kit | https://www.figma.com/community/file/1035203688168086460 | Material Design | 页面抓取未显示许可 | 官方 M3 组件与动态色、形状库的 Figma 源；搭 Helium 组件库时作结构参考 |
| 14 | Personal AI Agent Mobile App UI/UX Design | https://dribbble.com/shots/27838405-Personal-AI-Agent-Mobile-App-UI-UX-Design | Mostafijur Rahman / Dribbble | 未声明，默认版权保留 | 浅色高对比 + 玻璃感卡片；收件箱、日程、订机票全流程聊天化；取色 #96B8F7 #6AB4E7 #D3E7F6 #1E64BB #111419 |
| 15 | AI Assistant Landing Page – Bento Card Design | https://dribbble.com/shots/26354368-AI-Assistant-Landing-Page-Bento-Card-Design | Omotive / Dribbble | 未声明，默认版权保留 | bento 每格讲一个能力、整体保持视觉和谐；丁香-粉-靛配色 #8A92D8 #9B84AE #EDCFEB |
| 16 | Google Gemini iOS Chat Interface | https://mobbin.com/explore/screens/e26aa019-fb49-4aaf-8d2c-10586a07d4cb | Mobbin（截图归档） | 截图版权归原应用 | 空状态结构：问候 + 输入 + 麦克风 + 相机，极简入口 |

未能核实 / 未纳入：Riffs 单条作品的独立页面（只核实了 feed 页）；design.google 的 Expressive 研究文章（只看到搜索摘要，未打开，故不列入）；Mobbin 需登录才能看完整流程。

---

## 8. 下一步建议
1. 在 Figma 建 token（色 / 圆角 / 弹簧 / 字体轴），把 6 张概念稿重绘为可编辑的矢量稿（概念图的像素文字不可直接交付）。
2. 先做两个英雄时刻的动效原型：Agent 光球形状变形、审批卡浮起→批准→飘走。
3. 暗色模式与无障碍对比度检查（饱和卡上的白字需逐一验 WCAG AA）。
