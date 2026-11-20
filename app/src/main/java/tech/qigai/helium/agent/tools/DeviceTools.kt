package tech.qigai.helium.agent.tools

import org.json.JSONObject
import tech.qigai.helium.agent.Risk
import tech.qigai.helium.tasks.Parsed
import tech.qigai.helium.tasks.TaskArgs
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Asks for a runtime permission; true when granted. Off-screen callers (background runs) get false, never a dialog. */
fun interface PermissionGate {
    suspend fun ensure(permission: String): Boolean
}

object Perms {
    const val READ_CALENDAR = "android.permission.READ_CALENDAR"
    const val POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"
}

data class CalEvent(
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val location: String = "",
    val calendar: String = "",
)

/** Instances in [begin, end) from the device calendar (CalendarContract in the app). */
fun interface CalendarSource {
    suspend fun events(begin: Long, end: Long): List<CalEvent>
}

/** Posts a plain notification. Returns false when notifications are off or not permitted. */
interface Notifier {
    fun canNotify(): Boolean
    fun post(title: String, body: String): Boolean
}

/** Range of whole local days [startDate, startDate + days). */
data class DayRange(val start: LocalDate, val days: Int) {
    val endExclusive: LocalDate get() = start.plusDays(days.toLong())
    fun beginMs(zone: ZoneId) = start.atStartOfDay(zone).toInstant().toEpochMilli()
    fun endMs(zone: ZoneId) = endExclusive.atStartOfDay(zone).toInstant().toEpochMilli()
}

object CalendarArgs {
    fun parse(args: JSONObject, today: LocalDate): Parsed<DayRange> {
        val raw = args.optString("date").trim()
        val start = if (raw.isEmpty()) today else TaskArgs.parseDate(raw, today) ?: when (raw.lowercase()) {
            "yesterday", "昨天" -> today.minusDays(1)
            else -> return Parsed.Err("date 格式不对：「$raw」，请用 YYYY-MM-DD / today / tomorrow")
        }
        val days = if (args.has("days") && !args.isNull("days")) args.optInt("days", 1) else 1
        if (days !in 1..31) return Parsed.Err("days 要在 1 到 31 之间")
        return Parsed.Ok(DayRange(start, days))
    }

    /**
     * All-day instances are stored as UTC midnights, so on a UTC+8 phone a query for the next local day also returns
     * the previous all-day event. Keep an all-day event only when its UTC dates overlap the local dates asked for.
     */
    fun keep(e: CalEvent, range: DayRange): Boolean {
        if (!e.allDay) return true
        val s = Instant.ofEpochMilli(e.begin).atZone(ZoneOffset.UTC).toLocalDate()
        val endMs = if (e.end > e.begin) e.end else e.begin + 86_400_000L
        val eEx = Instant.ofEpochMilli(endMs - 1).atZone(ZoneOffset.UTC).toLocalDate().plusDays(1)
        return s.isBefore(range.endExclusive) && eEx.isAfter(range.start)
    }

    private val dayFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd EEEE", Locale.CHINA)

    fun format(events: List<CalEvent>, range: DayRange, zone: ZoneId): String {
        val head = if (range.days == 1) range.start.format(dayFmt) else "${range.start.format(dayFmt)} 起 ${range.days} 天"
        val kept = events.filter { keep(it, range) }.sortedWith(compareBy({ !it.allDay }, { it.begin }))
        if (kept.isEmpty()) return "$head：日历里没有安排。"
        val sb = StringBuilder("$head（时区 ${zone.id}），共 ${kept.size} 项：\n")
        val byDay = kept.groupBy { e ->
            if (e.allDay) Instant.ofEpochMilli(e.begin).atZone(ZoneOffset.UTC).toLocalDate().coerceAtLeast(range.start)
            else Instant.ofEpochMilli(e.begin).atZone(zone).toLocalDate().coerceAtLeast(range.start)
        }.toSortedMap()
        byDay.forEach { (day, list) ->
            if (range.days > 1) sb.append(day.format(dayFmt)).append('\n')
            list.forEach { e ->
                sb.append("- ")
                if (e.allDay) sb.append("全天")
                else {
                    val b = Instant.ofEpochMilli(e.begin).atZone(zone)
                    val en = Instant.ofEpochMilli(e.end).atZone(zone)
                    sb.append("%02d:%02d–%02d:%02d".format(b.hour, b.minute, en.hour, en.minute))
                    if (en.toLocalDate() != b.toLocalDate()) sb.append("（次日）")
                }
                sb.append(' ').append(e.title.ifBlank { "（无标题）" })
                if (e.location.isNotBlank()) sb.append(" @ ").append(e.location)
                if (e.calendar.isNotBlank()) sb.append("［").append(e.calendar).append("］")
                sb.append('\n')
            }
        }
        return sb.toString().trimEnd()
    }

    private fun LocalDate.coerceAtLeast(min: LocalDate) = if (isBefore(min)) min else this
}

class CalendarReadTool(
    private val source: CalendarSource,
    private val permissions: PermissionGate,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val clock: () -> Long = System::currentTimeMillis,
) : Tool {
    override val spec = ToolSpec(
        "calendar_read",
        "读取手机本机日历里的日程（只读）。问“明天有什么安排”“这周忙不忙”时调用。首次使用会弹系统权限。",
        JSONObject().put("type", "object").put(
            "properties",
            JSONObject()
                .put("date", JSONObject().put("type", "string").put("description", "起始日期：YYYY-MM-DD 或 today / tomorrow；默认今天"))
                .put("days", JSONObject().put("type", "integer").put("description", "读几天，1–31，默认 1")),
        ),
    )

    override fun risk(args: JSONObject) = Risk.LOW
    override val readsPrivate get() = true
    override fun target(args: JSONObject) = "calendar"
    override fun title(args: JSONObject) = "读取日历"

    override suspend fun run(args: JSONObject, ctx: ToolContext): ToolOutput {
        val z = zone()
        val today = Instant.ofEpochMilli(clock()).atZone(z).toLocalDate()
        val range = when (val p = CalendarArgs.parse(args, today)) {
            is Parsed.Err -> return ToolOutput(p.message, isError = true)
            is Parsed.Ok -> p.value
        }
        if (!permissions.ensure(Perms.READ_CALENDAR)) {
            return ToolOutput("没有读取日历的权限。请在前台打开 Helium 再问一次并允许「日历」权限，或在系统设置里为 Helium 开启。", isError = true)
        }
        val events = source.events(range.beginMs(z), range.endMs(z))
        return ToolOutput(CalendarArgs.format(events, range, z))
    }
}

class NotifyTool(private val notifier: Notifier, private val permissions: PermissionGate) : Tool {
    override val spec = ToolSpec(
        "notify",
        "立刻在手机上发一条通知（标题 + 正文）。用于后台任务汇报结果，或用户要求“发个通知给我”。定时提醒请用 reminder_create。",
        tech.qigai.helium.agent.tools.params(
            Triple("title", "string", "通知标题，简短"),
            Triple("body", "string", "通知正文"),
            required = listOf("title"),
        ),
    )

    override fun risk(args: JSONObject) = Risk.LOW
    override fun target(args: JSONObject) = "notification"
    override fun title(args: JSONObject) = "发通知：" + args.optString("title").take(20)

    override suspend fun run(args: JSONObject, ctx: ToolContext): ToolOutput {
        val title = args.optString("title").trim()
        if (title.isEmpty()) return ToolOutput("缺少 title", isError = true)
        if (!notifier.canNotify() && !permissions.ensure(Perms.POST_NOTIFICATIONS)) {
            return ToolOutput("通知权限没有开启，发不出去。请在系统设置里允许 Helium 发通知。", isError = true)
        }
        return if (notifier.post(title.take(60), args.optString("body").trim().take(2000))) ToolOutput("已发送通知「${title.take(60)}」")
        else ToolOutput("通知被系统关闭了，发不出去。", isError = true)
    }
}
