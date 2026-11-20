package tech.qigai.helium.agent.tools

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import tech.qigai.helium.agent.Risk
import tech.qigai.helium.tasks.Parsed
import tech.qigai.helium.tasks.TaskArgs
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** One event to insert into the device calendar. All-day events use UTC midnights, timed ones local time. */
data class NewCalEvent(
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val location: String = "",
    val description: String = "",
)

/** Inserts [event] into the device calendar; the new event id, or null when there is no usable calendar. */
fun interface CalendarWriter {
    suspend fun insert(event: NewCalEvent): Long?
}

object CalendarWriteArgs {
    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val dayFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd EEEE", java.util.Locale.CHINA)

    /**
     * Accepts what the model plausibly writes: `start` as "2026-11-21 15:00" / ISO datetime / "明天 15:00" / a
     * bare date (→ all-day), or `date` + `time` separately ("2026-11-21" + "15:00" / "下午3点"; no `time` → all-day).
     * `end` is a datetime like `start`; otherwise `durationMinutes` (default 60) past the start.
     */
    fun parse(args: JSONObject, zone: ZoneId, today: LocalDate): Parsed<NewCalEvent> {
        val title = args.optString("title").trim()
        if (title.isEmpty()) return Parsed.Err("缺少 title：要新建的日程标题")
        val location = args.optString("location").trim()
        val description = args.optString("description").trim()
        val explicitAllDay = if (args.has("allDay") && !args.isNull("allDay")) args.optBoolean("allDay") else null

        val startRaw = args.optString("start").trim()
        val (date, time) = if (startRaw.isNotEmpty()) {
            parseDateTime(startRaw, today) ?: return Parsed.Err("start 格式不对：「$startRaw」，请用 YYYY-MM-DD HH:mm、YYYY-MM-DD 或“明天 15:00”这样的写法")
        } else {
            val dateRaw = args.optString("date").trim()
            val d = if (dateRaw.isEmpty()) today else TaskArgs.parseDate(dateRaw, today)
                ?: return Parsed.Err("date 格式不对：「$dateRaw」，请用 YYYY-MM-DD / today / tomorrow")
            val timeRaw = args.optString("time").trim()
            val t: Pair<Int, Int>? = if (timeRaw.isEmpty()) {
                null
            } else {
                TaskArgs.parseTime(timeRaw)
                    ?: return Parsed.Err("time 格式不对：「$timeRaw」，请用 15:00 或“下午3点”这样的写法")
            }
            d to t
        }
        val allDay = explicitAllDay ?: (time == null)
        if (!allDay && time == null) return Parsed.Err("没有给开始时间：请给 time（如 15:00）或 start（如 YYYY-MM-DD HH:mm）")
        val begin: Long
        val end: Long
        if (allDay) {
            val endRaw = args.optString("end").trim()
            val endDate = if (endRaw.isEmpty()) date.plusDays(1)
            else TaskArgs.parseDate(endRaw, today) ?: parseDateTime(endRaw, today)?.first
                ?: return Parsed.Err("end 格式不对：「$endRaw」")
            if (!endDate.isAfter(date)) return Parsed.Err("全天日程的结束日期要在开始日期之后")
            begin = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            end = endDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        } else {
            val (h, m) = time!!
            begin = date.atTime(h, m).atZone(zone).toInstant().toEpochMilli()
            val endRaw = args.optString("end").trim()
            end = when {
                endRaw.isEmpty() -> {
                    val dur = if (args.has("durationMinutes") && !args.isNull("durationMinutes")) args.optInt("durationMinutes", 60) else 60
                    if (dur !in 1..60 * 24 * 7) return Parsed.Err("durationMinutes 要在 1 到 10080 之间")
                    begin + dur * 60_000L
                }
                else -> parseDateTime(endRaw, today)?.let { (d, t) ->
                    val (eh, em) = t ?: return Parsed.Err("end 要带时间：「$endRaw」")
                    d.atTime(eh, em).atZone(zone).toInstant().toEpochMilli()
                } ?: return Parsed.Err("end 格式不对：「$endRaw」")
            }
            if (end <= begin) return Parsed.Err("结束时间要在开始时间之后")
        }
        return Parsed.Ok(NewCalEvent(title, begin, end, allDay, location, description))
    }

    /** "2026-11-21 15:00" / ISO datetime / "明天 15:00" / bare date → (date, time?) with time null for a bare date. */
    fun parseDateTime(raw: String, today: LocalDate): Pair<LocalDate, Pair<Int, Int>?>? {
        val s = raw.trim()
        runCatching { LocalDateTime.parse(s) }.getOrNull()?.let { return it.toLocalDate() to (it.hour to it.minute) }
        runCatching { LocalDateTime.parse(s, dateFmt) }.getOrNull()?.let { return it.toLocalDate() to (it.hour to it.minute) }
        val parts = s.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (parts.size == 2) {
            val d = TaskArgs.parseDate(parts[0], today) ?: return null
            val t = TaskArgs.parseTime(parts[1]) ?: return null
            return d to t
        }
        if (parts.size == 1) {
            TaskArgs.parseDate(parts[0], today)?.let { return it to null }
        }
        return null
    }

    fun format(event: NewCalEvent, zone: ZoneId): String {
        val sb = StringBuilder()
        if (event.allDay) {
            val d = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(event.begin), ZoneOffset.UTC).toLocalDate()
            val e = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(event.end), ZoneOffset.UTC).toLocalDate()
            sb.append(d.format(dayFmt))
            if (e > d.plusDays(1)) sb.append(" – ").append(e.minusDays(1).format(dayFmt))
            sb.append("（全天）")
        } else {
            val b = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(event.begin), zone)
            val e = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(event.end), zone)
            sb.append(b.format(dateFmt)).append(" – ").append(e.format(dateFmt))
        }
        if (event.location.isNotBlank()) sb.append("\n地点：").append(event.location)
        return sb.toString()
    }
}

/**
 * HIGH risk, always "仅这一次": the approval card shows the full event before anything is written. The device
 * calendar is the only calendar the app can write to — there is no email tool, so nothing here sends anything.
 */
class CalendarWriteTool(
    private val writer: CalendarWriter,
    private val permissions: PermissionGate,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val clock: () -> Long = System::currentTimeMillis,
) : Tool {
    override val spec = ToolSpec(
        "calendar_write",
        "在手机本机日历里新建一个日程（需要用户先在审批卡上批准，卡上会显示完整的日程）。start 用 \"YYYY-MM-DD HH:mm\"，只有日期就是全天；也可以分开给 date 和 time。",
        params(
            Triple("title", "string", "日程标题"),
            Triple("start", "string", "开始时间：\"YYYY-MM-DD HH:mm\"、\"明天 15:00\" 或只有日期（全天）"),
            Triple("date", "string", "日期：YYYY-MM-DD / today / tomorrow；和 time 分开给时用"),
            Triple("time", "string", "开始时间：15:00 或“下午3点”；不给就是全天"),
            Triple("end", "string", "结束时间，格式同 start；不给按 durationMinutes 算"),
            Triple("durationMinutes", "integer", "时长（分钟），默认 60"),
            Triple("allDay", "boolean", "是否全天"),
            Triple("location", "string", "地点（可选）"),
            Triple("description", "string", "备注（可选）"),
            required = listOf("title"),
        ),
    )

    override fun risk(args: JSONObject) = Risk.HIGH
    override fun target(args: JSONObject) = "calendar"
    override fun title(args: JSONObject) = "新建日程「" + args.optString("title").trim().ifEmpty { "（无标题）" } + "」"
    override fun detail(args: JSONObject): String {
        val z = zone()
        val today = java.time.Instant.ofEpochMilli(clock()).atZone(z).toLocalDate()
        return when (val p = CalendarWriteArgs.parse(args, z, today)) {
            is Parsed.Err -> "标题：${args.optString("title")}\n${p.message}"
            is Parsed.Ok -> "标题：${p.value.title}\n时间：${CalendarWriteArgs.format(p.value, z)}\n" +
                if (p.value.description.isNotBlank()) "备注：${p.value.description}" else "（新建后可在日历 App 里修改或删除）"
        }
    }

    override suspend fun run(args: JSONObject, ctx: ToolContext): ToolOutput {
        val z = zone()
        val today = java.time.Instant.ofEpochMilli(clock()).atZone(z).toLocalDate()
        val event = when (val p = CalendarWriteArgs.parse(args, z, today)) {
            is Parsed.Err -> return ToolOutput(p.message, isError = true)
            is Parsed.Ok -> p.value
        }
        if (!permissions.ensure(WRITE_CALENDAR)) {
            return ToolOutput("没有写入日历的权限。请在前台打开 Helium 再问一次并允许「日历」权限，或在系统设置里为 Helium 开启。", isError = true)
        }
        val id = writer.insert(event) ?: return ToolOutput("日程没有写进去：手机里没有可用的日历。", isError = true)
        return ToolOutput("已在日历里新建「${event.title}」（${CalendarWriteArgs.format(event, z)}），id=$id。")
    }

    companion object {
        const val WRITE_CALENDAR = "android.permission.WRITE_CALENDAR"
    }
}

/** Writes through CalendarContract.Events into the primary visible calendar. */
class AndroidCalendarWriter(private val context: Context) : CalendarWriter {
    override suspend fun insert(event: NewCalEvent): Long? = withContext(Dispatchers.IO) {
        val calId = primaryCalendarId() ?: return@withContext null
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calId)
            put(CalendarContract.Events.TITLE, event.title)
            put(CalendarContract.Events.DTSTART, event.begin)
            put(CalendarContract.Events.DTEND, event.end)
            put(CalendarContract.Events.EVENT_TIMEZONE, if (event.allDay) "UTC" else ZoneId.systemDefault().id)
            put(CalendarContract.Events.ALL_DAY, if (event.allDay) 1 else 0)
            put(CalendarContract.Events.EVENT_LOCATION, event.location)
            put(CalendarContract.Events.DESCRIPTION, event.description)
        }
        val uri = runCatching { context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values) }.getOrNull()
            ?: return@withContext null
        runCatching { ContentUris.parseId(uri) }.getOrNull()
    }

    private fun primaryCalendarId(): Long? {
        val projection = arrayOf(CalendarContract.Calendars._ID)
        val cursor = runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI, projection,
                "${CalendarContract.Calendars.VISIBLE} = 1", null,
                "${CalendarContract.Calendars.IS_PRIMARY} DESC, ${CalendarContract.Calendars._ID} ASC",
            )
        }.getOrNull() ?: return null
        cursor.use { if (it.moveToFirst()) return it.getLong(0) }
        return null
    }
}
