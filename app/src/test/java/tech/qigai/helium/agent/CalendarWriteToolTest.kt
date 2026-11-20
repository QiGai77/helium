package tech.qigai.helium.agent

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.qigai.helium.agent.tools.CalendarWriteArgs
import tech.qigai.helium.agent.tools.CalendarWriteTool
import tech.qigai.helium.agent.tools.CalendarWriter
import tech.qigai.helium.agent.tools.NewCalEvent
import tech.qigai.helium.agent.tools.PermissionGate
import tech.qigai.helium.agent.tools.ToolContext
import tech.qigai.helium.tasks.Parsed
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class CalendarWriteToolTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val today = LocalDate.of(2026, 11, 20)

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int) =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, zone).toInstant().toEpochMilli()

    private fun utc(y: Int, m: Int, d: Int) =
        LocalDate.of(y, m, d).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test fun `start datetime defaults to a 60 minute event`() {
        val p = CalendarWriteArgs.parse(JSONObject().put("title", "开会").put("start", "2026-11-21 15:00"), zone, today)
        assertTrue(p is Parsed.Ok)
        val e = (p as Parsed.Ok).value
        assertEquals("开会", e.title)
        assertEquals(at(2026, 11, 21, 15, 0), e.begin)
        assertEquals(at(2026, 11, 21, 16, 0), e.end)
        assertFalse(e.allDay)
    }

    @Test fun `date plus time, and chinese words for tomorrow`() {
        val p = CalendarWriteArgs.parse(
            JSONObject().put("title", "评审").put("date", "2026-11-21").put("time", "下午3点").put("durationMinutes", 30),
            zone, today,
        ) as Parsed.Ok
        assertEquals(at(2026, 11, 21, 15, 0), p.value.begin)
        assertEquals(at(2026, 11, 21, 15, 30), p.value.end)

        val p2 = CalendarWriteArgs.parse(JSONObject().put("title", "评审").put("start", "明天 15:00"), zone, today) as Parsed.Ok
        assertEquals(at(2026, 11, 21, 15, 0), p2.value.begin)
    }

    @Test fun `a bare date is an all-day event in utc`() {
        val p = CalendarWriteArgs.parse(JSONObject().put("title", "放假").put("start", "2026-11-21"), zone, today) as Parsed.Ok
        assertTrue(p.value.allDay)
        assertEquals(utc(2026, 11, 21), p.value.begin)
        assertEquals(utc(2026, 11, 22), p.value.end)
    }

    @Test fun `bad arguments are errors the model can correct`() {
        assertTrue(CalendarWriteArgs.parse(JSONObject().put("start", "2026-11-21 15:00"), zone, today) is Parsed.Err)
        assertTrue(CalendarWriteArgs.parse(JSONObject().put("title", "x").put("start", "下周三大概"), zone, today) is Parsed.Err)
        assertTrue(CalendarWriteArgs.parse(JSONObject().put("title", "x").put("start", "2026-11-21 15:00").put("end", "2026-11-21 14:00"), zone, today) is Parsed.Err)
        assertTrue(CalendarWriteArgs.parse(JSONObject().put("title", "x").put("date", "2026-11-21").put("time", "25:00"), zone, today) is Parsed.Err)
        assertTrue(CalendarWriteArgs.parse(JSONObject().put("title", "x").put("date", "2026-11-21").put("allDay", false), zone, today) is Parsed.Err)
    }

    @Test fun `tool is high risk and writes through the writer only with permission`() = runBlocking {
        val written = mutableListOf<NewCalEvent>()
        val writer = CalendarWriter { e -> written += e; 42L }
        val tool = CalendarWriteTool(writer, PermissionGate { true }, zone = { zone }, clock = { at(2026, 11, 20, 9, 0) })
        assertEquals(Risk.HIGH, tool.risk(JSONObject()))
        assertEquals(Risk.HIGH, RiskPolicy.floor("calendar_write"))
        val out = tool.run(JSONObject().put("title", "开会").put("start", "2026-11-21 15:00").put("durationMinutes", 30), ToolContext("c"))
        assertFalse(out.isError)
        assertTrue(out.text.contains("已在日历里新建"))
        assertEquals(1, written.size)
        assertEquals(at(2026, 11, 21, 15, 30), written.single().end)

        val denied = CalendarWriteTool(writer, PermissionGate { false }, zone = { zone })
        val out2 = denied.run(JSONObject().put("title", "开会").put("start", "2026-11-21 15:00"), ToolContext("c"))
        assertTrue(out2.isError)
        assertTrue(out2.text.contains("没有写入日历的权限"))
        assertEquals("nothing written without permission", 1, written.size)

        val noCal = CalendarWriteTool(CalendarWriter { null }, PermissionGate { true }, zone = { zone })
        assertTrue(noCal.run(JSONObject().put("title", "开会").put("start", "2026-11-21 15:00"), ToolContext("c")).isError)
    }

    @Test fun `detail shows the full event for the approval card`() {
        val tool = CalendarWriteTool(CalendarWriter { 1L }, PermissionGate { true }, zone = { zone }, clock = { at(2026, 11, 20, 9, 0) })
        val d = tool.detail(JSONObject().put("title", "开会").put("start", "2026-11-21 15:00").put("location", "3 楼会议室"))
        assertTrue(d.contains("开会"))
        assertTrue(d.contains("2026-11-21 15:00"))
        assertTrue(d.contains("3 楼会议室"))
    }
}
