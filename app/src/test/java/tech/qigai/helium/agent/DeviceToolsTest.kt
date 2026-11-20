package tech.qigai.helium.agent

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.qigai.helium.agent.tools.CalEvent
import tech.qigai.helium.agent.tools.CalendarArgs
import tech.qigai.helium.agent.tools.CalendarReadTool
import tech.qigai.helium.agent.tools.DayRange
import tech.qigai.helium.agent.tools.Notifier
import tech.qigai.helium.agent.tools.NotifyTool
import tech.qigai.helium.agent.tools.ToolContext
import tech.qigai.helium.tasks.Parsed
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class DeviceToolsTest {
    private val sh = ZoneId.of("Asia/Shanghai")
    private val now = ZonedDateTime.of(2026, 11, 20, 12, 37, 0, 0, sh).toInstant().toEpochMilli()
    private fun local(d: Int, h: Int, m: Int) = ZonedDateTime.of(2026, 11, d, h, m, 0, 0, sh).toInstant().toEpochMilli()
    private fun utcMidnight(d: Int) = ZonedDateTime.of(2026, 11, d, 0, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test fun calendarArgs() {
        val today = LocalDate.of(2026, 11, 20)
        assertEquals(DayRange(today, 1), (CalendarArgs.parse(JSONObject(), today) as Parsed.Ok).value)
        assertEquals(DayRange(today.plusDays(1), 1), (CalendarArgs.parse(JSONObject("""{"date":"tomorrow"}"""), today) as Parsed.Ok).value)
        assertEquals(DayRange(LocalDate.of(2026, 12, 1), 7), (CalendarArgs.parse(JSONObject("""{"date":"2026-12-01","days":7}"""), today) as Parsed.Ok).value)
        assertTrue(CalendarArgs.parse(JSONObject("""{"days":40}"""), today) is Parsed.Err)
        assertTrue(CalendarArgs.parse(JSONObject("""{"date":"someday"}"""), today) is Parsed.Err)
    }

    @Test fun allDayEventsDoNotLeakIntoNextLocalDay() {
        val birthday = CalEvent("生日", utcMidnight(21), utcMidnight(22), allDay = true)
        assertTrue(CalendarArgs.keep(birthday, DayRange(LocalDate.of(2026, 11, 21), 1)))
        assertFalse(CalendarArgs.keep(birthday, DayRange(LocalDate.of(2026, 11, 22), 1)))
        assertFalse(CalendarArgs.keep(birthday, DayRange(LocalDate.of(2026, 11, 20), 1)))
    }

    @Test fun readsTomorrowWithPermission() = runBlocking {
        var asked: String? = null
        var range: Pair<Long, Long>? = null
        val tool = CalendarReadTool(
            { b, e -> range = b to e; listOf(CalEvent("产品评审", local(21, 9, 0), local(21, 10, 0), false, "3楼", "工作"), CalEvent("生日", utcMidnight(21), utcMidnight(22), true)) },
            { p -> asked = p; true }, { sh }, { now },
        )
        val out = tool.run(JSONObject("""{"date":"tomorrow"}"""), ToolContext("c"))
        assertEquals("android.permission.READ_CALENDAR", asked)
        assertEquals(local(21, 0, 0) to local(22, 0, 0), range)
        assertTrue(out.text, out.text.contains("2026-11-21") && out.text.contains("09:00–10:00 产品评审 @ 3楼") && out.text.contains("全天 生日"))
        assertTrue(out.text.indexOf("全天") < out.text.indexOf("09:00"))
    }

    @Test fun calendarWithoutPermissionExplains() = runBlocking {
        val out = CalendarReadTool({ _, _ -> error("must not query") }, { false }, { sh }, { now }).run(JSONObject(), ToolContext("c"))
        assertTrue(out.isError); assertTrue(out.text.contains("权限"))
    }

    @Test fun emptyDay() = runBlocking {
        val out = CalendarReadTool({ _, _ -> emptyList() }, { true }, { sh }, { now }).run(JSONObject(), ToolContext("c"))
        assertTrue(out.text.contains("没有安排"))
    }

    @Test fun notifyTool() = runBlocking {
        val posted = mutableListOf<Pair<String, String>>()
        val n = object : Notifier {
            var allowed = true
            override fun canNotify() = allowed
            override fun post(title: String, body: String): Boolean { posted += title to body; return allowed }
        }
        assertFalse(NotifyTool(n) { true }.run(JSONObject("""{"title":"早报","body":"晴"}"""), ToolContext("c")).isError)
        assertEquals("早报" to "晴", posted.single())
        n.allowed = false
        assertTrue(NotifyTool(n) { false }.run(JSONObject("""{"title":"x"}"""), ToolContext("c")).isError)
        assertTrue(NotifyTool(n) { true }.run(JSONObject("""{"body":"x"}"""), ToolContext("c")).isError)
    }

}
