package tech.qigai.helium.tasks

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/*
 * Pure scheduling logic (no Android): when a task fires next, how long to back off after a failure, and how a
 * schedule reads in Chinese. Everything takes the zone and the clock as arguments so it is unit-testable.
 */

enum class TaskKind(val label: String) { ONCE("一次"), DAILY("每天"), WEEKLY("每周") }

/** AGENT runs the prompt through a headless agent run; NOTIFY just posts the text as a reminder (exact alarm). */
enum class TaskAction(val label: String) { AGENT("Agent 任务"), NOTIFY("提醒") }

/** Weekday bitmask, Mon = 1 … Sun = 64 (bit `dayOfWeek.value - 1`), the format of `scheduled_tasks.weekdays`. */
object Weekdays {
    const val ALL = 0b1111111
    const val WORKDAYS = 0b0011111
    const val WEEKEND = 0b1100000
    private val CN = arrayOf("一", "二", "三", "四", "五", "六", "日")

    fun bit(day: Int): Int = 1 shl (day - 1)
    fun has(mask: Int, day: Int): Boolean = mask and bit(day) != 0
    fun of(vararg days: Int): Int = days.fold(0) { m, d -> if (d in 1..7) m or bit(d) else m }
    fun days(mask: Int): List<Int> = (1..7).filter { has(mask, it) }

    fun label(mask: Int): String = when (mask and ALL) {
        ALL -> "每天"
        WORKDAYS -> "工作日"
        WEEKEND -> "周末"
        0 -> "未选星期"
        else -> "每周" + days(mask).joinToString("、") { CN[it - 1] }
    }
}

data class ScheduleSpec(
    val kind: TaskKind,
    val hour: Int,
    val minute: Int,
    /** Only for WEEKLY. */
    val weekdays: Int = Weekdays.ALL,
    /** Only for ONCE: the absolute fire time in epoch millis. */
    val onceAt: Long? = null,
) {
    val timeOfDay: String get() = "%02d:%02d".format(hour, minute)

    companion object {
        /** Parses the stored "HH:mm" form; falls back to 08:00 on garbage so a bad row never crashes the scheduler. */
        fun of(kind: String, timeOfDay: String, weekdays: Int, onceAt: Long?): ScheduleSpec {
            val k = runCatching { TaskKind.valueOf(kind) }.getOrDefault(TaskKind.ONCE)
            val (h, m) = TaskArgs.parseTime(timeOfDay) ?: (8 to 0)
            return ScheduleSpec(k, h, m, weekdays, onceAt)
        }
    }
}

object Schedule {
    /**
     * First fire time strictly after [after] (epoch millis), or null when the task will never fire again
     * (a one-shot in the past, or a weekly task with no weekdays). Local wall-clock time in [zone]: a daily 08:00
     * task stays at 08:00 across DST changes; a time that falls in a DST gap moves forward like [ZonedDateTime.of].
     */
    fun nextFire(spec: ScheduleSpec, after: Long, zone: ZoneId): Long? {
        val time = LocalTime.of(spec.hour.coerceIn(0, 23), spec.minute.coerceIn(0, 59))
        return when (spec.kind) {
            TaskKind.ONCE -> spec.onceAt?.takeIf { it > after }
            TaskKind.DAILY -> firstMatching(after, zone, time) { true }
            TaskKind.WEEKLY -> if (spec.weekdays and Weekdays.ALL == 0) null
            else firstMatching(after, zone, time) { Weekdays.has(spec.weekdays, it.dayOfWeek.value) }
        }
    }

    private inline fun firstMatching(after: Long, zone: ZoneId, time: LocalTime, ok: (LocalDate) -> Boolean): Long? {
        val start = Instant.ofEpochMilli(after).atZone(zone).toLocalDate()
        for (i in 0..8) {
            val d = start.plusDays(i.toLong())
            if (!ok(d)) continue
            val at = ZonedDateTime.of(d, time, zone).toInstant().toEpochMilli()
            if (at > after) return at
        }
        return null
    }

    /** "每天 08:00", "工作日 08:00", "每周一、三 20:30", "11月21日 08:00". */
    fun describe(spec: ScheduleSpec, zone: ZoneId): String = when (spec.kind) {
        TaskKind.DAILY -> "每天 ${spec.timeOfDay}"
        TaskKind.WEEKLY -> "${Weekdays.label(spec.weekdays)} ${spec.timeOfDay}"
        TaskKind.ONCE -> spec.onceAt?.let { formatAt(it, zone) } ?: "一次 ${spec.timeOfDay}"
    }

    /** "11月21日 周六 08:00" (with the year only when it differs from [now]). */
    fun formatAt(at: Long, zone: ZoneId, now: Long = System.currentTimeMillis()): String {
        val t = Instant.ofEpochMilli(at).atZone(zone)
        val n = Instant.ofEpochMilli(now).atZone(zone)
        val wd = arrayOf("一", "二", "三", "四", "五", "六", "日")[t.dayOfWeek.value - 1]
        val y = if (t.year != n.year) "${t.year}年" else ""
        return "$y${t.monthValue}月${t.dayOfMonth}日 周$wd %02d:%02d".format(t.hour, t.minute)
    }
}

/**
 * Exponential backoff for failed runs: base × 2^(n−1), capped. With the defaults: 1, 2, 4, 8, 16 minutes and then
 * the run gives up until the next regular fire. Retries also wait for a network connection (WorkManager constraint),
 * so an offline phone retries as soon as it reconnects after the delay.
 */
object Backoff {
    const val BASE_MS = 60_000L
    const val MAX_MS = 2 * 3600_000L
    const val MAX_RETRIES = 5

    /** Delay before retry number [failures] (1 = first retry after the first failure). */
    fun delayMs(failures: Int, base: Long = BASE_MS, max: Long = MAX_MS): Long {
        if (failures <= 0) return 0
        val shift = (failures - 1).coerceAtMost(30)
        val d = base.toDouble() * (1L shl shift).toDouble()
        return if (d >= max) max else d.toLong()
    }

    /** Null when [failures] used up the retry budget. */
    fun nextRetryAt(failures: Int, now: Long, maxRetries: Int = MAX_RETRIES): Long? =
        if (failures > maxRetries) null else now + delayMs(failures)

    fun label(ms: Long): String = when {
        ms < 60_000 -> "${ms / 1000} 秒"
        ms < 3600_000 -> "${ms / 60_000} 分钟"
        else -> "%.1f 小时".format(ms / 3600_000.0).replace(".0 ", " ")
    }
}

/** Human-readable, short failure reasons for the run history. */
object FailureReason {
    private val network = listOf(
        "unable to resolve host", "unknownhost", "failed to connect", "network is unreachable", "no address associated",
        "connection reset", "connection refused", "timeout", "timed out", "software caused connection abort", "econn",
    )

    fun classify(raw: String?): String {
        val msg = raw?.trim().orEmpty().ifEmpty { "未知错误" }
        val l = msg.lowercase()
        return when {
            network.any { l.contains(it) } -> "网络不可用：${msg.take(120)}"
            l.contains("401") || l.contains("unauthorized") || l.contains("invalid api key") -> "模型 API Key 无效或未设置：${msg.take(120)}"
            l.contains("429") || l.contains("rate limit") -> "模型限流：${msg.take(120)}"
            else -> msg.take(200)
        }
    }
}
