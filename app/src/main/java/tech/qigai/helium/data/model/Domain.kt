package tech.qigai.helium.data.model

import tech.qigai.helium.ui.illo.IlloKind

/* UI models for the screens. Values come from Room repositories and, later, real data sources. */

enum class Source(val label: String) { ALL("全部"), X("X"), MEMORY("记忆"), MAIL("邮件"), WEB("网页") }

data class Highlight(val title: String, val source: String, val sourceKind: Source, val time: String, val relevance: String)

data class XPost(
    val author: String,
    val handle: String,
    val initials: String,
    val time: String,
    val text: String,
    val likes: String,
    val reposts: String,
    val replies: String,
    val reason: String,
)

enum class CardSize(val label: String) { SMALL("1×1 小"), WIDE("2×1 宽"), LARGE("2×2 大") }

sealed interface GlanceCard {
    val id: String

    data class Flight(
        override val id: String, val title: String, val meta: String, val time: String, val route: String,
    ) : GlanceCard

    data class Train(override val id: String, val title: String, val meta: String, val time: String, val detail: String) : GlanceCard
    data class Weather(override val id: String, val city: String, val temp: String, val detail: String, val kind: IlloKind) : GlanceCard
    data class Budget(override val id: String, val remaining: String, val usedFraction: Float, val detail: String) : GlanceCard
    data class Stock(
        override val id: String, val title: String, val price: String, val change: String, val up: Boolean,
        val points: List<Float>, val refresh: String,
    ) : GlanceCard

    data class Package(override val id: String, val title: String, val meta: String, val count: Int) : GlanceCard
    data class Custom(
        override val id: String, val title: String, val meta: String, val lines: List<Pair<String, String>>, val size: CardSize,
    ) : GlanceCard
}

data class GlanceToday(
    val updatedAt: String,
    val highlights: List<Highlight>,
    val xPost: XPost,
    val cards: List<GlanceCard>,
)

data class HeroEvent(val tag: String, val title: String, val meta: String, val time: String, val route: String, val kind: IlloKind)

data class HomeSummary(val activeCount: Int, val hero: HeroEvent, val pendingApproval: String, val glanceDelta: String)

enum class TaskIcon { SUN, RECEIPT, PLANE, MAIL, CHART }

data class ScheduledTask(val id: String, val title: String, val schedule: String, val icon: TaskIcon, val enabled: Boolean, val weekdays: Set<Int>)

data class Project(val id: String, val title: String, val badge: String, val todo: Int, val next: String, val progress: Float, val icon: TaskIcon)

data class MemoryItem(val id: String, val text: String, val inferred: Boolean = false)

enum class SkillIcon { DINING, PLANE, BAG, MAIL }

data class Skill(val id: String, val title: String, val meta: String, val icon: SkillIcon, val enabled: Boolean, val running: Boolean = false)

data class ApprovalLine(val label: String, val value: String)

data class Approval(
    val id: String,
    val kind: String,
    val title: String,
    val origin: String,
    val lines: List<ApprovalLine>,
    val total: String,
    val card: String,
    val shipTo: String,
    val eta: String,
    val action: String,
)

enum class StepState { DONE, RUNNING, WAITING, FUTURE }

data class TimelineStep(
    val title: String,
    val time: String?,
    val state: StepState,
    val chips: List<String> = emptyList(),
    val thumbs: Int = 0,
    val note: String? = null,
)

data class AgentRun(val title: String, val status: String, val done: Int, val total: Int, val steps: List<TimelineStep>)

data class LiveStep(val text: String, val done: Boolean)

data class LiveRun(val title: String, val elapsed: String, val steps: List<LiveStep>)
