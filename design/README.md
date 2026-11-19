# Helium · 矢量设计稿（SVG）v0.3

由 `design/01–06-*.png` 的 AI 概念稿重绘而成的**可编辑**矢量稿。v0.3 = 速览 / 我的 / 模型页 + 事件插画 + 新导航（在 v0.2 收敛配色上增量修改；v0.2 留档在 `v0.2/`，v0.1 在 `v0.1/`）。全部手写 SVG（Python 生成），无位图、无文字转路径、无第三方 logo / emoji / Google 商标。

| 文件 | 画板 | 内容 |
|---|---|---|
| `00-components.svg` | 1680×2640 | Token 与组件表（v0.2 全部内容）+ **v0.3 区**：11 种事件插画 × hero/light 两版、新底部导航（三个根页选中态 + 无选中态）、头像入口、来源 chip、AI 生成角标、Home 提示行、设置列表行、自定义卡入口与 AI 卡 |
| `01-home.svg` | 412×892 | 首页简洁版：问候 + 「现在最要紧」航班英雄卡（飞机插画）+ 2 行轻提示 + Agent 光球 + 输入胶囊 + 导航 |
| `02-chat.svg` | 412×892 | 对话（同 v0.2，对话详情页无底栏） |
| `03-timeline.svg` | 412×892 | 任务时间线（同 v0.2） |
| `04-approval.svg` | 412×892 | 审批（同 v0.2） |
| `05-memory-skills.svg` | 412×892 | 记忆与技能：改为「我的」下的二级页，加返回钮，底栏为新四项、无选中 |
| `06-schedule-projects.svg` | 412×892 | 任务（定时与项目）：标题改「任务」，右上 + 与头像，底栏「任务」选中 |
| `07-glance.svg` | 412×1744 长页 | 速览：日期 + 今日速览 + 来源 chip、今日要点英雄卡（3 条信息差）、X 热帖卡、我的卡片 bento（航班/高铁/天气/预算/AI 自定义股价卡/包裹叠卡）、「让 Helium 做一张卡」入口 |
| `08-glance-custom.svg` | 412×892 | 速览 · 自定义卡 sheet：输入 → 预览 → 尺寸 1×1/2×1/2×2 → 刷新频率 → 添加 |
| `09-me.svg` | 412×1554 长页 | 我的：资料英雄卡 + 账户 / 能力 / 记忆与文件 / 安全 / 通用 五组 |
| `10-models.svg` | 412×892 | 模型与推理：OpenCode Go（默认）/ Gemini API / 自带 API Key |
| `png/*@2x.png` | 2x | Chromium 渲染预览 |
| `helium-svg-board.png` / `.jpg` | 5456×14220 | 总览板（PNG 13.8MB，另存 JPG 3.9MB） |
| `v0.2/`、`v0.1/` | — | 历史版本 SVG、PNG、board、README、src 原样留档 |
| `src/` | — | 生成脚本：`hel.py`（组件、token、事件插画）、`screens.py`（11 张画板）、`render.py`、`board.py`、`build.sh` |

## v0.3 · 速览 / 我的 / 事件插画（2026-11-19）

配色沿用 v0.2（用户认可）：单一强调色 he-447、白/雾卡、1px #E6E8F0 + 6% 阴影、圆角 20/28/32、按钮 56/48/36、8dp。长页规则放宽为「**每个视口 1 个英雄渐变**」（速览首屏 = 今日要点，滚动后 = 航班卡）。

### 事件插画（`hel.illo(kind, x, y, scale, tone)`）
- 11 种：`flight` 飞机 · `train` 高铁 · `sunny` / `cloudy` / `rain` 天气 · `package` 包裹 · `truck` 货车 · `hotel` 酒店楼 · `dining` 餐具 · `calendar` 日历 · `chart` 迷你走势。
- 120×96 网格，2.5D：主面（竖向渐变）/ 顶面 / 侧面三档明度 + 一条顶边高光线 + 一个小光斑 + 落地软影；圆角线端，手绘感但几何干净。
- `tone='hero'`：白 95→70% / 80% / 48% + ink 22% 细节，只放在当屏英雄渐变右侧或底部；`tone='light'`：强调色雾面（#F3F5FF→#D6DCFF）+ 强调色细节，放白卡右上，缩到 0.56–0.66×。
- 插画只做气氛，不承载信息；无第三方 logo / emoji。
- 图层名 `illo-<kind>`，Figma 里是普通矢量组，可直接改透明度或换色。

### 导航与入口
- 底栏改为 **首页 · 对话 · 速览 · 任务**（新图标 `glance`、`tasks`）；「我的」= 各根页右上角头像（`btn-avatar`）。
- 二级页（05 记忆与技能）底栏保留但无选中态（`bottom_nav(None)`）；09/10 为设置类二级页，不显示底栏。

### 每屏改动
- **00 组件表**：新增 v0.3 区（插画 22 格、导航 4 态、头像入口、来源 chip、AI 角标、提示行、设置行、自定义卡）；Bento 示例换成带飞机插画的航班卡；导航区换新四项。
- **01 首页**：删掉预算/天气/包裹 bento（移到速览）；只留「现在最要紧」航班英雄卡（208 高，飞机插画 1.3× 在右上）+ 2 行提示（琥珀点「1 件待你确认」、强调色点「今日速览已更新」）+ 居中光球。
- **05 记忆与技能**：加返回钮；底栏换新四项、无选中。
- **06 任务**：标题「任务」+ 辅文「定时与项目」；右上 + 新建 与头像；底栏「任务」选中。
- **07 速览（新）**：来源 chip（全部 / X / 记忆 / 邮件 / 网页）；今日要点 Aurora 卡（3 条，每条 = 标题 + 来源·时间 + 「与你相关」）；X 热帖卡（作者、摘要、互动量、「推给你」理由条）；我的卡片：航班（飞机）、高铁（列车）、天气（多云）、预算、AI 生成的英伟达股价卡（角标「AI 生成 · 可编辑」+ 走势 + 涨跌标签）、包裹叠卡（包裹插画）、虚线「+ 让 Helium 做一张卡」。
- **08 自定义卡（新）**：底部 sheet，输入框（强调色聚焦描边 + 重新生成）→ 预览卡（AI 角标）→ 尺寸三选一 → 刷新频率 chip → 来源说明 → 「添加到速览」。
- **09 我的（新）**：资料卡（Aurora，套餐标签）；账户（个人资料 / Google 账号「连接 Gmail · 日历 · 云端硬盘」已连接 / 模型与推理「默认模型 OpenCode Go · 可选 Gemini API（需 API Key）」）、能力（连接器 / 技能 / MCP）、记忆与文件（记忆 / 系统文件 / 项目空间）、安全（审批规则 / 权限与审计 / 凭据保险箱）、通用（通知 / 外观 / 语言 / 关于）；退出登录。
- **10 模型与推理（新）**：三个提供方卡——OpenCode Go（默认 · 已启用，API Key 接入；GLM-5.3、Kimi K3、DeepSeek V4 Pro、Qwen3.8 Max）；Gemini API（未配置，Google AI Studio API Key，按钮「填入你的 Gemini API Key」）；自带 API Key（OpenAI 兼容，Base URL + Key + 模型名）。底部说明「API Key 存放在凭据保险箱」。Google 账号只做连接器，不做模型登录；页面无 Google logo。

### 示例数据说明
速览里的资讯、X 帖子、账号 handle、股价、用户名「林夏」都是**占位示例**，不是真实数据。

## v0.2 · 收敛配色精修（2026-11-19）

反馈：「颜色略微有点太花」。方向是**收敛而不是去色**：不回到"近白单调卡面 + 单色扁平图标"，也不做全暗线框。

### Figma Riffs 参考（借鉴点）
| # | Riff | 作者 | URL | 借鉴到 Helium 的点 |
|---|---|---|---|---|
| 1 | Card shuffle（卡组顶牌飞出、其余前移） | Alejandro Vasquez | https://www.figma.com/riffs/573ca127-c0de-4d04-90c1-f94e8f52d6f5 | 只有**最前一张**是暖色渐变卡，后面的卡只露 10–15% 色带且压暗 → 一屏只给 1 个英雄渐变，叠卡后层改白/雾 |
| 2 | Five glossy cards, one continuous shuffle | waqarhsyed | https://www.figma.com/riffs/a2d24278-2ca5-488b-994a-649fe4fb210f | 材质感来自"柔和反光 + 浅色渐变罩面 + 顶边高光"，而不是更多颜色 → 英雄卡加斜向光带、顶边高光描边、6% 颗粒 |
| 3 | Artificial Design: an orbit of AI tools | Hijrat | https://www.figma.com/community/feed（feed 内条目） | "glass light sweep" + 统一圆角体系 → 光带做成 `*-gloss` 图层；圆角收成 行 20 / 卡 28 / 面板 32 三档 |
| 4 | Magnetic Player（Spotify 风叠卡） | Hijrat | https://www.figma.com/riffs/82709a3f-d316-42f8-a413-00a47a479b63 | 大面积中性底 + **单一强调色**（只给进度条和关键字）+ 1px 发丝描边 + 柔影 → 卡片统一 1px #E6E8F0 描边 + 6% 低阴影 |
| 5 | Flowing tab bar（lime pill 弹簧拉伸） | Crafttor | https://www.figma.com/community/feed（feed 内条目，源：capturemotion.dev） | 中性导航条上只有一个强调色胶囊，标签随选中淡入 → 底部导航选中态 = 强调色浅底胶囊 + 图标 + 文字 |
| 6 | Bento UI Components（12 套 bento 网格） | DD UIUX | https://www.figma.com/riffs/c9a8f549-aafc-47ea-8cee-0e71d2fd98a3 | 每组 bento 只有一格带渐变光球，其余是中性格 + 统一间距/圆角 → Home 只留航班卡为英雄，预算/天气改白卡 + 玻璃图标 |
| 7 | Liquid Dial（液态时间拨盘） | Crafttor | https://www.figma.com/community/feed（feed 内条目） | 只有一条"有光泽的液体带"承载颜色 → 时间线进度条做成唯一英雄：雾面 Aurora→Lagoon + 上半高光 |

（Riffs 单页正文为动态加载，静态抓取只能读到描述与作者；视觉细节来自上一轮浏览器内查看的记录 `riffs-layout.md`。各 riff 未标许可，只借鉴手法，不复制作品。）

### 强调色使用规则（同步写在 00-components 色板区）
- **每屏 1 个英雄渐变**：Home=航班卡；对话=Live 卡渐变描边；时间线=总进度条；审批=审批卡头部；记忆与技能=正在运行的技能卡（只有"工作中"的东西发光，呼应 §1 惰性）；定时与项目=当前项目卡（搬家）。
- 其余卡片：白卡（#FFFFFF + 1px #E6E8F0 + 阴影 dy6/blur12/#1B2150 6%）或雾卡（#F0F2F8 / #F6F7FB）。
- **单一强调色 he-447 #4F6BFF**：选中、进度、开关、链接、图标描边、"进行中"标签。主按钮仍用 ink 深色。
- **状态色**（琥珀待审批 / 薄荷完成 / 珊瑚警示）只出现在 ≤24dp 的点、标签、徽标上，不再做卡底或大描边。
- 谱线色只活在英雄渐变内部；渐变饱和度 −20%（`desat(k=.8)`），Aurora 亮度 −6% 保证白字对比；mesh 色块放大 1.3×、模糊加大、透明度 −15%。
- 背景去掉所有彩色光斑，粒子 ≤6 个、仅强调色/ink-3、18–25% 透明。
- 输入胶囊：渐变描边 2px→1px，透明度 空闲 22% / 输入 45% / 语音 70% / 运行 55%，去掉外发光，加白卡同款描边与阴影。
- 图标：统一"玻璃底"——白→#E9ECF5 渐变底 + 内部强调色晕（第二层）+ 顶部高光描边 + 发丝描边，图标本身用强调色；英雄卡上是 20% 白磨砂底 + 白图标。保留扇贝/花/胶囊/软方等形状语义。

### 每屏改动
- **01 Home**：只留航班卡为 Aurora 英雄（加航班号副信息、玻璃航线胶囊）；预算 Sunrise 卡 → 白卡 + 强调色进度条；天气/包裹 → 白卡 + 玻璃图标；叠卡后层改雾色；Agent 光球闲置态改玻璃静止；补底部导航（composer 在上、导航在下，间距 24）；副标题降为 ink-2 17sp。
- **02 对话**：顶部思考辉光从四色改为单一蓝紫雾；Live 卡描边 2→1.5px 为唯一英雄；步骤完成徽标缩到 22dp 薄荷，进行中波浪改强调色渐隐线；浏览器预览改中性；顶栏按钮统一 40dp 玻璃圆钮、y=56；加时间戳分隔；状态改"● 正在工作"。
- **03 时间线**：进度条为唯一英雄（雾面 + 高光）；节点改单一强调色（完成=实心圆、执行中=描边软方、等待=琥珀空心圈、未来=灰方）；轨道中性灰 + 已完成段强调色；当前步骤由 Lilac 渐变卡改白卡 + 强调色描边 + "进行中"标签；待审批卡去掉琥珀大描边，只留琥珀点；chip 改雾色；"去审批"右边距对齐 20。
- **04 审批**：Sunrise 只在卡头；去掉背景暖色光晕；后层叠卡改白/雾；投影从珊瑚色改中性；"待审批"标签改白底 + 琥珀点 + 8dp 圆角（修正文字被裁切）；所有金额 tabular；顶栏标题改"待你确认"。
- **05 记忆与技能**：记忆胶囊从 4 种彩色渐变改白胶囊 + 发丝描边 + 虚线"添加"；技能卡只有"订机票（进行中）"是 Aurora 英雄，其余白卡 + 极淡强调色晕 + 玻璃形状图标；开关统一强调色；标题字号对齐 Headline 32；技能卡高度 160、行距 16。
- **06 定时与项目**：任务图标改玻璃底 + 强调色（去掉琥珀/珊瑚/蓝三色底）；项目叠卡只留当前"搬家计划"为 Aurora 英雄，"东京旅行"去掉 Sunrise 改白卡，"求职"改雾卡 + 薄荷小标签；去掉与导航冲突的浮动"新建定时任务"按钮，改为标题右侧 + 圆钮；补底部导航；"新建"右对齐 388。
- **00 组件表**：新增强调色使用规则区、v0.1/v0.2 谱线色对照、状态标签、玻璃图标、按钮 56/48/36 三档、圆角三档、光球闲置/工作中两态、阴影与规则清单。

### 全局精修
8dp 网格（页边 24、卡间 16、卡内边 20/24）；顶栏按钮统一 y=56；底部导航统一 y=800；按钮高度三档 56/48/36；卡片圆角三档 20/28/32；阴影统一一套；所有数字 `tabular-nums`；正文 15–16 / 辅文 12–13 ink-2 / 占位 ink-3。

### 导入 Figma 补充
- 英雄卡新增 `*-gloss`（光带）、`*-grain`（颗粒，`feTurbulence` + `mix-blend-mode: soft-light`）、`*-rim`（顶边高光渐变描边）。**Figma 不支持 feTurbulence**，`*-grain` 导入后可能为空或变成灰块，直接删掉该图层即可（或在 Figma 用 Noise 效果 6% 重做）。
- 底部导航选中态的文字是独立 Text 图层，可做 Crafttor 式"胶囊拉伸 + 文字淡入"原型。

## 导入 Figma

1. **先在本机安装字体**，否则文字会回退成默认字体：
   - Google Sans Flex（OFL）：google/fonts 仓库 `ofl/googlesansflex/`，或 github.com/googlefonts/googlesans-flex
   - Noto Sans SC（OFL）：Google Fonts 或 github.com/notofonts/noto-cjk
   - Figma 网页版需要装 Figma 字体助手（Font Installer）才能读本机字体。
2. 任选其一：
   - **拖入**：把 `.svg` 直接拖到 Figma 画布，每个文件成为一个 Frame。
   - **Paste as SVG**：用文本编辑器打开 SVG 全选复制，在 Figma 里粘贴（Figma 会识别 SVG 源码）。
3. 导入后：
   - 文字是可编辑的 Text 图层。`font-family` 写的是 `Google Sans Flex, Noto Sans SC, sans-serif`，但 Figma 每个文字图层只认一种字体：拉丁 / 数字会落到 Google Sans Flex；**中文图层请批量改为 Noto Sans SC**（选中 → 替换字体，或用 "Mixed fonts" 插件）。
   - mesh 渐变 = 底层线性渐变 + 若干径向渐变椭圆 + 高斯模糊，放在 `*-fill` 组里，被卡片圆角裁切（Figma 里是 mask 组）。要换色，改 `*-fill` 里椭圆的渐变色标即可。
   - 阴影是 `feDropShadow`，Figma 会转成 Drop shadow 效果；彩色阴影的颜色与透明度会保留。若某个版本没转出来，按 `00-components` 里的规则手动加：白卡 dy 8 / blur 12 / #4F6BFF 8%，饱和卡 dy 12 / blur 14 / 主色 30%。
   - `font-variation-settings`（ROND 100）和 `tabular-nums` 写在大数字的 style 里，Figma 导入时会忽略；在 Figma 里给数字图层手动设置可变轴 Roundness 100、Tabular figures。
   - 渐变描边（输入胶囊、实时操作卡）是 `stroke="url(#…)"`，Figma 支持渐变描边。
4. 建议导入后：把 `00-components` 里的组件逐个 "Create component"，把色值录入 Variables（名称同 token：`he-447`、`ink-2`、`radius/xl` …）。

## 图层命名规则

- 每个逻辑对象都是 `<g id="语义名">`，Figma 用 id 作为图层名。小写英文 + 连字符，结构 `类型-对象[-状态]`：
  - 系统层：`background`（`canvas`、`bg-orbs`、`bg-particles`）、`status-bar`（`time`、`signal`、`wifi`、`battery`）、`gesture-bar`
  - 卡片：`card-flight`、`card-budget`、`card-weather`、`card-live-run`、`card-approval`、`skill-card-restaurant` …
  - 卡片内部：`*-shadow`（投影底板）、`*-fill`（mesh 渐变组）、`*-bg` / `*-tint`（浅卡底与色晕）、`label`、`title`、`meta`、`amount`、`time`
  - 组件：`composer`（`composer-glow` / `-fill` / `-stroke`、`btn-attach`、`btn-skills`、`btn-mic`、`btn-stop`、`placeholder`）、`btn-*`、`chip-*`、`switch-on` / `switch-off`、`segmented`、`bottom-nav`（`nav-*`、`nav-*-active`）
  - 时间线：`rail`、`nodes`（`node-1-done`、`node-4-running`、`node-5-waiting`、`node-6-future`）、`step-N-状态`
  - 叠卡：`wallet-*`、`stack-back-2`、`stack-back-1`、`stack-front`；项目卡 `project-*`
  - 图标：`icon-名称`（24px 网格、2px 圆角描边，可整体改 stroke 色）
- 同名对象自动加序号（`icon-check-2`），保证每个 id 在文件内唯一。

## 相对概念稿的修正

- 时间线：概念稿写"已完成 4/6"但只画了 3 个完成步骤 → 改为 **3/6**，进度条 50%；节点按 DESIGN §4 形状语义统一（完成=实心圆、执行中=软方、等待你=空心琥珀圈、未来=灰方），去掉混用的"饼干"节点；底部操作栏不再压住最后一张步骤卡。
- 饱和卡数量：Home 原有 4 张饱和卡 → 只保留航班（Aurora）+ 预算（Sunrise），天气与包裹改浅渐变卡；技能页 4 张饱和 → 2 饱和 + 2 浅卡；项目叠卡 3 张饱和 → 1 Aurora + 1 Sunrise + 1 浅薄荷；审批页背后的叠卡改为浅色。
- 审批：`待审批` 标签改 8px 小圆角（DESIGN §4"张力"）；标题用 ink；收货地写到区一级，金额 / 收货地 / 付款方式同屏；安全承诺加盾牌图标。
- 对话：Agent 运行中时输入胶囊变为"继续说…"+ 停止按钮（DESIGN §6.1），不再显示麦克风；Agent 回复加扇贝花头像。
- 记忆与技能：分段控件改为"全部 / 记忆 / 技能"，与页面同时展示两类内容一致；补上状态栏；底部导航改为 4 个入口并高亮当前页。
- 输入胶囊：附件 `+` 与技能入口（星形）分开；空闲态描边 30% 透明。
- 状态栏统一 9:41；所有图标为自绘线性图标，无品牌 logo。

## 重新生成

```bash
cd design/svg/src && ./build.sh      # 生成 SVG 并用 headless Chromium 渲染 2x PNG
python3 board.py                     # 拼总览板（>4MB 自动另存 JPG）
# 长页（07、09）的高度由内容计算；新增画板在 screens.py 末尾 s07–s10
```
渲染依赖：Playwright Chromium；字体放 `~/.fonts` 后 `fc-cache -f`。
