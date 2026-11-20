package tech.qigai.helium.glance

import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** Batched "为什么推给你": one model call for many items, grounded in the user's memories. */
object ReasonPrompt {
    const val SYSTEM = "你是 Helium 的速览编辑。根据用户的记忆，为每条信息写一句不超过 30 字的中文理由，说明它为什么和这位用户有关。" +
        "只根据给出的记忆推断，不要编造用户没有说过的事；实在无关就写「你订阅了 <来源>」。" +
        "只输出一个 JSON 对象，键是条目编号，值是理由，例如 {\"1\":\"你在准备换 Android 手机\"}。"

    data class Entry(val id: Long, val title: String, val source: String)

    fun user(entries: List<Entry>, memories: List<String>): String = buildString {
        append("用户的记忆：\n")
        if (memories.isEmpty()) append("（暂无）\n") else memories.take(30).forEach { append("- ").append(it.take(120)).append('\n') }
        append("\n条目：\n")
        entries.forEachIndexed { i, e -> append(i + 1).append(". [").append(e.source.take(20)).append("] ").append(e.title.take(120)).append('\n') }
    }

    /** Maps the model reply back to item ids. Accepts the JSON object or "1. 理由" lines. */
    fun parse(reply: String, entries: List<Entry>): Map<Long, String> {
        val byIndex = mutableMapOf<Int, String>()
        CardDefinition.extractJsonObject(reply)?.let { raw ->
            runCatching {
                val o = JSONObject(raw)
                o.keys().forEach { k -> k.trim().toIntOrNull()?.let { byIndex[it] = o.optString(k) } }
            }
        }
        if (byIndex.isEmpty()) {
            Regex("(?m)^\\s*\\[?(\\d{1,3})]?\\s*[.:：、)）]\\s*(.+)$").findAll(reply).forEach { m ->
                byIndex[m.groupValues[1].toInt()] = m.groupValues[2]
            }
        }
        return byIndex.mapNotNull { (i, r) ->
            val e = entries.getOrNull(i - 1) ?: return@mapNotNull null
            val clean = r.trim().trim('"', '“', '”').replace(Regex("\\s+"), " ").take(40)
            if (clean.isEmpty()) null else e.id to clean
        }.toMap()
    }
}

/** Formats a calendar instance into the Home hero's tag / time / countdown. */
object HeroFormat {
    data class Hero(val tag: String, val title: String, val meta: String, val time: String, val route: String)

    fun event(title: String, begin: Long, end: Long, location: String?, allDay: Boolean, now: Long, zone: ZoneId): Hero {
        val b = Instant.ofEpochMilli(begin).atZone(zone)
        val n = Instant.ofEpochMilli(now).atZone(zone)
        val day = dayWord(n, b)
        val time = if (allDay) "全天" else "%02d:%02d".format(b.hour, b.minute)
        val route = when {
            begin <= now && now < end -> "进行中"
            allDay -> day
            else -> countdown(end = begin, now = now)
        }
        val meta = listOfNotNull(
            "$day${if (allDay) "" else " " + period(b.hour)}",
            if (!allDay) "%02d:%02d–%02d:%02d".format(b.hour, b.minute, Instant.ofEpochMilli(end).atZone(zone).hour, Instant.ofEpochMilli(end).atZone(zone).minute) else null,
            location?.trim()?.ifBlank { null },
        ).joinToString(" · ")
        return Hero(tag = "$day · 日程", title = title.ifBlank { "（无标题日程）" }, meta = meta, time = time, route = route)
    }

    fun dayWord(now: ZonedDateTime, at: ZonedDateTime): String {
        val d = ChronoUnit.DAYS.between(now.toLocalDate(), at.toLocalDate())
        return when (d) {
            0L -> "今天"
            1L -> "明天"
            2L -> "后天"
            else -> "${at.monthValue}月${at.dayOfMonth}日"
        }
    }

    fun period(hour: Int) = when (hour) {
        in 0..5 -> "凌晨"
        in 6..11 -> "上午"
        12 -> "中午"
        in 13..17 -> "下午"
        else -> "晚上"
    }

    fun countdown(end: Long, now: Long): String {
        val min = ((end - now) / 60_000).coerceAtLeast(0)
        return when {
            min < 1 -> "马上开始"
            min < 60 -> "$min 分钟后"
            min < 24 * 60 -> "${min / 60} 小时后"
            else -> "${min / (24 * 60)} 天后"
        }
    }
}
