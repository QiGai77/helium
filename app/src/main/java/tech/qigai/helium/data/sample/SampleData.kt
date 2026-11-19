package tech.qigai.helium.data.sample

import tech.qigai.helium.ui.illo.IlloKind

/**
 * 示例数据 — placeholder content from the v0.3 design boards. Not real flights, posts, prices or people.
 * Everything rendered from here is marked with a 「示例」 tag in the UI.
 */
object SampleData : HomeSource, GlanceSource, TaskSource, MemorySource, ApprovalSource, RunSource {

    private val flight = HeroEvent(
        tag = "现在最要紧",
        title = "航班 · 周六",
        meta = "东航 MU523 · 已提醒值机",
        time = "18:40",
        route = "PVG → HND",
        kind = IlloKind.FLIGHT,
    )

    override fun summary() = HomeSummary(
        activeCount = 3,
        hero = flight,
        pendingApproval = "降噪耳机 · ¥1,299",
        glanceDelta = "3 条信息差",
    )

    override fun today() = GlanceToday(
        updatedAt = "08:00",
        highlights = listOf(
            Highlight("多家模型 API 降价，长上下文降幅最大", "X · @llm_pricing", Source.X, "2 小时前", "Helium 按 token 计费"),
            Highlight("手机 Agent 的「审批」交互成讨论热点", "网页 · 设计周刊", Source.WEB, "今早", "你在做审批卡设计"),
            Highlight("周六东京有小雨，出发当天建议带伞", "邮件 + 记忆", Source.MAIL, "刚刚", "周六 MU523 出行"),
        ),
        xPost = XPost(
            author = "Agent Lab",
            handle = "@agentlab_dev",
            initials = "AL",
            time = "3 小时前",
            text = "手机上的 Agent 真正难的不是对话，\n而是让人放心交出付款和发消息：\n每一步都要可见、可暂停、可撤回。",
            likes = "2.4k",
            reposts = "860",
            replies = "132",
            reason = "推给你：你在设计 Agent 审批流程 · 来自记忆",
        ),
        cards = listOf(
            GlanceCard.Flight("flight", "航班 · 周六", "东航 MU523 · 已提醒值机", "18:40", "PVG → HND"),
            GlanceCard.Train("train", "高铁", "周日出发", "09:12", "G7012 · 虹桥 → 杭州东"),
            GlanceCard.Weather("weather", "上海 · 多云", "22°", "最高 24° · 最低 16°", IlloKind.CLOUDY),
            GlanceCard.Budget("budget", "¥2,340", 0.61f, "已用 61% · 共 ¥6,000"),
            GlanceCard.Stock(
                "stock", "英伟达 · NVDA", "\$186.42", "+2.1%", true,
                listOf(0.42f, 0.48f, 0.40f, 0.52f, 0.46f, 0.58f, 0.55f, 0.66f, 0.62f, 0.78f), "每小时刷新",
            ),
            GlanceCard.Package("package", "包裹 · 明天到达", "2 件 · 已到达转运中心", 2),
        ),
    )

    override fun scheduled() = listOf(
        ScheduledTask("brief", "每日早报", "每天 08:00", TaskIcon.SUN, true, setOf(1, 2, 3, 4, 5, 6, 7)),
        ScheduledTask("bills", "每周账单检查", "每周一 09:30", TaskIcon.RECEIPT, true, setOf(1)),
        ScheduledTask("checkin", "航班值机提醒", "周六 06:00 · 仅一次", TaskIcon.PLANE, true, setOf(6)),
    )

    override fun projects() = listOf(
        Project("job", "求职", "进行中", 5, "更新作品集 · 周五", 0.3f, TaskIcon.MAIL),
        Project("tokyo", "东京旅行", "周六出发", 6, "生成每日行程", 0.5f, TaskIcon.PLANE),
        Project("move", "搬家计划", "当前", 12, "预约搬家公司 · 周日", 0.65f, TaskIcon.SUN),
    )

    override fun memories() = listOf(
        MemoryItem("m1", "喜欢靠窗座位"),
        MemoryItem("m2", "不吃香菜"),
        MemoryItem("m3", "周五健身"),
        MemoryItem("m4", "妈妈生日 3 月"),
    )

    override fun memoryCount() = 126

    override fun skills() = listOf(
        Skill("dining", "订餐厅", "本月用了 3 次", SkillIcon.DINING, true),
        Skill("flight", "订机票", "东京行程 · 3/6", SkillIcon.PLANE, true, running = true),
        Skill("shopping", "比价购物", "已比 6 家", SkillIcon.BAG, true),
        Skill("mail", "整理邮件", "已关闭", SkillIcon.MAIL, false),
    )

    override fun pending() = listOf(
        Approval(
            id = "a1", kind = "购买", title = "降噪耳机 · 午夜蓝", origin = "来自任务：比价购物 · 已比 6 家",
            lines = listOf(ApprovalLine("商品", "¥1,299"), ApprovalLine("运费", "¥0")),
            total = "¥1,299", card = "•••• 4821", shipTo = "家 · 上海市浦东新区", eta = "预计 11月21日 送达",
            action = "批准并支付 ¥1,299",
        ),
        Approval(
            id = "a2", kind = "改签", title = "机票改签 · MU523", origin = "来自任务：东京行程规划",
            lines = listOf(ApprovalLine("改签费", "¥200"), ApprovalLine("差价", "¥60")),
            total = "¥260", card = "•••• 4821", shipTo = "—", eta = "改签后 周六 18:40 起飞",
            action = "批准并支付 ¥260",
        ),
        Approval(
            id = "a3", kind = "发送", title = "发送邮件给 2 人", origin = "来自任务：整理邮件",
            lines = listOf(ApprovalLine("收件人", "2 人"), ApprovalLine("附件", "行程单.pdf")),
            total = "—", card = "—", shipTo = "—", eta = "批准后立即发送",
            action = "批准并发送",
        ),
    )

    override fun timeline() = AgentRun(
        title = "东京行程规划",
        status = "代理执行中",
        done = 3,
        total = 6,
        steps = listOf(
            TimelineStep("搜索航班", "21:02", StepState.DONE, chips = listOf("3 个结果", "最低 ¥2,180")),
            TimelineStep("比较酒店价格", "21:10", StepState.DONE, thumbs = 3, note = "已比 12 家\n均价 ¥1,050/晚"),
            TimelineStep("读取日历空档", "21:18", StepState.DONE),
            TimelineStep("生成每日行程", null, StepState.RUNNING),
            TimelineStep("等待你审批", null, StepState.WAITING),
            TimelineStep("预订机票与酒店", null, StepState.FUTURE),
        ),
    )

    override fun live() = LiveRun(
        title = "正在操作浏览器",
        elapsed = "0:42",
        steps = listOf(LiveStep("打开预订页面", true), LiveStep("选择 19:00 · 2 位", true), LiveStep("确认空位", false)),
    )
}

/** Simple service locator so screens depend on interfaces, not on [SampleData] directly. */
object Sources {
    var home: HomeSource = SampleData
    var glance: GlanceSource = SampleData
    var tasks: TaskSource = SampleData
    var memory: MemorySource = SampleData
    var approvals: ApprovalSource = SampleData
    var runs: RunSource = SampleData
}
