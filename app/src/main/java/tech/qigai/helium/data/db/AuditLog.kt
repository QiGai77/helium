package tech.qigai.helium.data.db

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Outcome chips on the 审计 page; each maps to one or more audit kinds. */
enum class AuditOutcome(val label: String, val kinds: Set<String>) {
    ASKED("请求确认", setOf("ask")),
    APPROVED("已批准", setOf("approve")),
    REJECTED("拒绝/拦截", setOf("reject", "deny", "skip")),
    RAN("执行成功", setOf("run")),
    FAILED("失败", setOf("error")),
    TAINT("外部内容 taint", emptySet()),
    ;

    fun matches(a: AuditEntity): Boolean = if (this == TAINT) a.tainted || a.kind == "taint" else a.kind in kinds
}

/** Date range chips. [range] gives [from, to) in epoch ms for "now" in [zone]. */
enum class AuditPeriod(val label: String) {
    TODAY("今天"), WEEK("近 7 天"), MONTH("近 30 天"), ALL("全部");

    fun range(now: Long, zone: ZoneId): Pair<Long, Long> {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val start = when (this) {
            TODAY -> today
            WEEK -> today.minusDays(6)
            MONTH -> today.minusDays(29)
            ALL -> return 0L to Long.MAX_VALUE
        }.atStartOfDay(zone).toInstant().toEpochMilli()
        return start to end
    }
}

/** Filter applied to the audit log; the same filter decides what is exported. Null fields = no constraint. */
data class AuditFilter(
    val tool: String? = null,
    val outcome: AuditOutcome? = null,
    val from: Long? = null,
    val to: Long? = null,
) {
    fun matches(a: AuditEntity): Boolean =
        (tool == null || a.tool == tool) &&
            (outcome == null || outcome.matches(a)) &&
            (from == null || a.at >= from) &&
            (to == null || a.at < to)

    fun apply(rows: List<AuditEntity>): List<AuditEntity> = rows.filter(::matches)

    companion object {
        fun forDay(day: LocalDate, zone: ZoneId, tool: String? = null, outcome: AuditOutcome? = null) = AuditFilter(
            tool, outcome,
            day.atStartOfDay(zone).toInstant().toEpochMilli(),
            day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
        )

        fun of(period: AuditPeriod, now: Long, zone: ZoneId, tool: String? = null, outcome: AuditOutcome? = null): AuditFilter {
            if (period == AuditPeriod.ALL) return AuditFilter(tool, outcome)
            val (f, t) = period.range(now, zone)
            return AuditFilter(tool, outcome, f, t)
        }
    }
}

/** JSON export written through the Storage Access Framework. Format is versioned and stable. */
object AuditExport {
    const val FORMAT = "helium-audit/1"

    fun toJson(rows: List<AuditEntity>, filter: AuditFilter, exportedAt: Long, zone: ZoneId): String {
        val iso = DateTimeFormatter.ISO_OFFSET_DATE_TIME
        fun stamp(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).format(iso)
        val f = JSONObject()
        filter.tool?.let { f.put("tool", it) }
        filter.outcome?.let { f.put("outcome", it.name) }
        filter.from?.let { f.put("from", stamp(it)) }
        filter.to?.takeIf { it != Long.MAX_VALUE }?.let { f.put("to", stamp(it)) }
        val entries = JSONArray()
        rows.forEach { a ->
            entries.put(
                JSONObject()
                    .put("id", a.id)
                    .put("at", a.at)
                    .put("time", stamp(a.at))
                    .put("conversationId", a.conversationId)
                    .put("kind", a.kind)
                    .put("tool", a.tool)
                    .put("target", a.target)
                    .put("risk", a.risk ?: JSONObject.NULL)
                    .put("tainted", a.tainted)
                    .put("detail", a.detail),
            )
        }
        return JSONObject()
            .put("format", FORMAT)
            .put("exportedAt", stamp(exportedAt))
            .put("filter", f)
            .put("count", rows.size)
            .put("entries", entries)
            .toString(2)
    }

    fun fileName(exportedAt: Long, zone: ZoneId): String =
        "helium-audit-" + Instant.ofEpochMilli(exportedAt).atZone(zone).format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) + ".json"
}
