package tech.qigai.helium.tasks

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** A task as the user (or the model) described it, validated and ready to store. */
data class TaskDraft(
    val title: String,
    val prompt: String,
    val action: TaskAction,
    val spec: ScheduleSpec,
)

/** Partial edit from task_update(action=edit) or the detail screen. Null = keep. */
data class TaskPatch(
    val title: String? = null,
    val prompt: String? = null,
    val kind: TaskKind? = null,
    val hour: Int? = null,
    val minute: Int? = null,
    val weekdays: Int? = null,
    val onceAt: Long? = null,
)

enum class UpdateOp { PAUSE, RESUME, SKIP_NEXT, RUN_NOW, DELETE, EDIT }

data class TaskUpdate(val id: String, val op: UpdateOp, val patch: TaskPatch = TaskPatch())

sealed interface Parsed<out T> {
    data class Ok<T>(val value: T) : Parsed<T>
    data class Err(val message: String) : Parsed<Nothing>
}

/**
 * Tolerant parsing of tool arguments. Models write times and weekdays in many shapes ("8:00", "08:00", "8点半",
 * [1,3,5], "一,三,五", "工作日"), so every field accepts the reasonable ones and reports a clear error otherwise;
 * the error text goes back to the model as the tool observation so it can correct itself.
 */
object TaskArgs {
    private val hm = Regex("^\\s*(\\d{1,2})\\s*[:：.]\\s*(\\d{1,2})\\s*$")
    private val cnHour = Regex("^\\s*(早上|上午|中午|下午|晚上|凌晨)?\\s*(\\d{1,2})\\s*[点時时]\\s*(半|(\\d{1,2})\\s*分?)?\\s*$")

    /** "08:00", "8:5", "8点", "8点半", "下午3点", "20:30" → (h, m). */
    fun parseTime(s: String?): Pair<Int, Int>? {
        val t = s?.trim().orEmpty()
        if (t.isEmpty()) return null
        hm.matchEntire(t)?.let { m ->
            val h = m.groupValues[1].toInt(); val mi = m.groupValues[2].toInt()
            return if (h in 0..23 && mi in 0..59) h to mi else null
        }
        cnHour.matchEntire(t)?.let { m ->
            var h = m.groupValues[2].toInt()
            val mi = when {
                m.groupValues[3] == "半" -> 30
                m.groupValues[4].isNotEmpty() -> m.groupValues[4].toInt()
                else -> 0
            }
            when (m.groupValues[1]) {
                "下午", "晚上" -> if (h in 1..11) h += 12
                "中午" -> if (h in 1..10) h += 12
            }
            return if (h in 0..23 && mi in 0..59) h to mi else null
        }
        return null
    }

    private val dayWords = mapOf(
        "一" to 1, "二" to 2, "三" to 3, "四" to 4, "五" to 5, "六" to 6, "日" to 7, "天" to 7, "七" to 7,
        "mon" to 1, "tue" to 2, "wed" to 3, "thu" to 4, "fri" to 5, "sat" to 6, "sun" to 7,
    )

    /** JSON array of 1..7 / names, or a string "1,3,5" / "一、三、五" / "周一到周五" / "工作日" / "周末" / "每天". 0 = none. */
    fun parseWeekdays(v: Any?): Int {
        when (v) {
            null, JSONObject.NULL -> return 0
            is Number -> return Weekdays.of(v.toInt())
            is JSONArray -> {
                var m = 0
                for (i in 0 until v.length()) m = m or parseWeekdays(v.opt(i))
                return m
            }
        }
        val s = v.toString().trim().lowercase()
        if (s.isEmpty()) return 0
        when (s) {
            "工作日", "weekdays", "workdays" -> return Weekdays.WORKDAYS
            "周末", "weekend", "weekends" -> return Weekdays.WEEKEND
            "每天", "daily", "everyday", "all" -> return Weekdays.ALL
        }
        Regex("(?:周|星期|礼拜)?([一二三四五六日天1-7])\\s*(?:到|至|-|~|—)\\s*(?:周|星期|礼拜)?([一二三四五六日天1-7])").matchEntire(s)?.let { m ->
            val a = dayOf(m.groupValues[1]) ?: return 0
            val b = dayOf(m.groupValues[2]) ?: return 0
            return if (a <= b) Weekdays.of(*(a..b).toList().toIntArray()) else 0
        }
        return s.split(Regex("[,，、\\s/]+")).filter { it.isNotBlank() }.fold(0) { acc, part ->
            val p = part.removePrefix("周").removePrefix("星期").removePrefix("礼拜")
            acc or (dayOf(p)?.let { Weekdays.bit(it) } ?: 0)
        }
    }

    private fun dayOf(p: String): Int? = p.toIntOrNull()?.takeIf { it in 1..7 } ?: dayWords[p] ?: dayWords[p.take(3)]

    fun parseKind(s: String?): TaskKind? = when (s?.trim()?.lowercase()) {
        "once", "one", "one-shot", "oneshot", "一次", "单次" -> TaskKind.ONCE
        "daily", "day", "每天", "每日" -> TaskKind.DAILY
        "weekly", "week", "每周", "每星期" -> TaskKind.WEEKLY
        else -> null
    }

    /** "2026-11-21", "today/今天", "tomorrow/明天", "后天". */
    fun parseDate(s: String?, today: LocalDate): LocalDate? {
        val t = s?.trim()?.lowercase().orEmpty()
        return when (t) {
            "", "today", "今天" -> if (t.isEmpty()) null else today
            "tomorrow", "明天" -> today.plusDays(1)
            "后天" -> today.plusDays(2)
            else -> runCatching { LocalDate.parse(t) }.getOrNull()
                ?: Regex("^(\\d{1,2})月(\\d{1,2})[日号]?$").matchEntire(t)?.let { m ->
                    runCatching { LocalDate.of(today.year, m.groupValues[1].toInt(), m.groupValues[2].toInt()) }.getOrNull()
                        ?.let { d -> if (d.isBefore(today)) d.plusYears(1) else d }
                }
        }
    }

    /**
     * Shared schedule part of task_create / reminder_create:
     * kind (once|daily|weekly; inferred from weekdays/date when missing), time "HH:mm", weekdays, date, in_minutes.
     */
    fun parseSchedule(args: JSONObject, now: Long, zone: ZoneId): Parsed<ScheduleSpec> {
        val inMinutes = if (args.has("in_minutes") && !args.isNull("in_minutes")) args.optDouble("in_minutes", Double.NaN) else Double.NaN
        val explicitKind = args.optString("kind").takeIf { it.isNotBlank() }
        val kind = explicitKind?.let { parseKind(it) ?: return Parsed.Err("kind 只能是 once / daily / weekly，收到「$it」") }
            ?: when {
                !inMinutes.isNaN() -> TaskKind.ONCE
                args.has("weekdays") && parseWeekdays(args.opt("weekdays")) != 0 -> TaskKind.WEEKLY
                args.optString("date").isNotBlank() -> TaskKind.ONCE
                else -> return Parsed.Err("缺少 kind（once / daily / weekly）")
            }

        if (kind == TaskKind.ONCE && !inMinutes.isNaN()) {
            if (inMinutes <= 0 || inMinutes > 60 * 24 * 366) return Parsed.Err("in_minutes 要在 1 到 527040 之间")
            val at = now + (inMinutes * 60_000).toLong()
            val t = Instant.ofEpochMilli(at).atZone(zone)
            return Parsed.Ok(ScheduleSpec(TaskKind.ONCE, t.hour, t.minute, Weekdays.ALL, at))
        }

        val timeRaw = args.optString("time")
        val (h, m) = parseTime(timeRaw) ?: return Parsed.Err(if (timeRaw.isBlank()) "缺少 time（24 小时制 HH:mm，例如 08:00）" else "time 格式不对：「$timeRaw」，请用 HH:mm")
        return when (kind) {
            TaskKind.DAILY -> Parsed.Ok(ScheduleSpec(TaskKind.DAILY, h, m, Weekdays.ALL))
            TaskKind.WEEKLY -> {
                val wd = parseWeekdays(args.opt("weekdays"))
                if (wd == 0) Parsed.Err("weekly 需要 weekdays，例如 [1,3,5]（1=周一 … 7=周日）")
                else if (wd == Weekdays.ALL) Parsed.Ok(ScheduleSpec(TaskKind.DAILY, h, m, Weekdays.ALL))
                else Parsed.Ok(ScheduleSpec(TaskKind.WEEKLY, h, m, wd))
            }
            TaskKind.ONCE -> {
                val nowZ = Instant.ofEpochMilli(now).atZone(zone)
                val dateRaw = args.optString("date")
                val date = if (dateRaw.isBlank()) null else parseDate(dateRaw, nowZ.toLocalDate()) ?: return Parsed.Err("date 格式不对：「$dateRaw」，请用 YYYY-MM-DD")
                val time = LocalTime.of(h, m)
                val at = if (date != null) ZonedDateTime.of(date, time, zone)
                else ZonedDateTime.of(nowZ.toLocalDate(), time, zone).let { if (it.toInstant().toEpochMilli() <= now) it.plusDays(1) else it }
                val ms = at.toInstant().toEpochMilli()
                if (ms <= now) Parsed.Err("这个时间（${Schedule.formatAt(ms, zone, now)}）已经过去了")
                else Parsed.Ok(ScheduleSpec(TaskKind.ONCE, h, m, Weekdays.ALL, ms))
            }
        }
    }

    /** task_create: title, prompt + schedule. */
    fun parseCreate(args: JSONObject, now: Long, zone: ZoneId): Parsed<TaskDraft> {
        val prompt = args.optString("prompt").trim()
        if (prompt.isEmpty()) return Parsed.Err("缺少 prompt：到点时 Helium 要做什么")
        val title = args.optString("title").trim().ifEmpty { prompt.lineSequence().first().take(16) }.take(40)
        return when (val s = parseSchedule(args, now, zone)) {
            is Parsed.Err -> s
            is Parsed.Ok -> Parsed.Ok(TaskDraft(title, prompt, TaskAction.AGENT, s.value))
        }
    }

    /** reminder_create: text + schedule (title defaults to the text). */
    fun parseReminder(args: JSONObject, now: Long, zone: ZoneId): Parsed<TaskDraft> {
        val text = args.optString("text").trim().ifEmpty { args.optString("prompt").trim() }
        if (text.isEmpty()) return Parsed.Err("缺少 text：提醒的内容，例如「喝水」")
        val title = args.optString("title").trim().ifEmpty { text.take(16) }.take(40)
        return when (val s = parseSchedule(args, now, zone)) {
            is Parsed.Err -> s
            is Parsed.Ok -> Parsed.Ok(TaskDraft(title, text, TaskAction.NOTIFY, s.value))
        }
    }

    fun parseOp(s: String?): UpdateOp? = when (s?.trim()?.lowercase()?.replace('-', '_')) {
        "pause", "暂停", "disable" -> UpdateOp.PAUSE
        "resume", "恢复", "enable" -> UpdateOp.RESUME
        "skip_next", "skip", "跳过", "跳过下一次" -> UpdateOp.SKIP_NEXT
        "run_now", "run", "立即运行", "现在运行" -> UpdateOp.RUN_NOW
        "delete", "remove", "删除" -> UpdateOp.DELETE
        "edit", "update", "修改", "编辑" -> UpdateOp.EDIT
        else -> null
    }

    /** task_update: id + action; for edit any of title, prompt, kind, time, weekdays, date. */
    fun parseUpdate(args: JSONObject, now: Long, zone: ZoneId): Parsed<TaskUpdate> {
        val id = args.optString("id").trim()
        if (id.isEmpty()) return Parsed.Err("缺少 id（先用 task_list 查）")
        val opRaw = args.optString("action")
        val op = parseOp(opRaw) ?: return Parsed.Err("action 只能是 pause / resume / skip_next / run_now / delete / edit，收到「$opRaw」")
        if (op != UpdateOp.EDIT) return Parsed.Ok(TaskUpdate(id, op))
        var patch = TaskPatch(
            title = args.optString("title").trim().takeIf { it.isNotEmpty() }?.take(40),
            prompt = args.optString("prompt").trim().takeIf { it.isNotEmpty() },
        )
        val touchesSchedule = listOf("kind", "time", "weekdays", "date", "in_minutes").any { args.has(it) && !args.isNull(it) }
        if (touchesSchedule) {
            if (args.has("time") && !args.has("kind") && !args.has("date") && !args.has("weekdays") && !args.has("in_minutes")) {
                val (h, m) = parseTime(args.optString("time")) ?: return Parsed.Err("time 格式不对：「${args.optString("time")}」，请用 HH:mm")
                patch = patch.copy(hour = h, minute = m)
            } else {
                when (val s = parseSchedule(args, now, zone)) {
                    is Parsed.Err -> return s
                    is Parsed.Ok -> patch = patch.copy(kind = s.value.kind, hour = s.value.hour, minute = s.value.minute, weekdays = s.value.weekdays, onceAt = s.value.onceAt)
                }
            }
        }
        if (patch == TaskPatch()) return Parsed.Err("edit 至少要改一项：title / prompt / time / kind / weekdays / date")
        return Parsed.Ok(TaskUpdate(id, op, patch))
    }
}
