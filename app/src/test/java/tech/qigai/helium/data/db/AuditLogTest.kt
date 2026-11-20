package tech.qigai.helium.data.db

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class AuditLogTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private fun at(d: Int, h: Int) = ZonedDateTime.of(2026, 11, d, h, 0, 0, 0, zone).toInstant().toEpochMilli()
    private fun oct(d: Int, h: Int) = ZonedDateTime.of(2026, 10, d, h, 0, 0, 0, zone).toInstant().toEpochMilli()
    private val now = at(20, 13)

    private val rows = listOf(
        AuditEntity(1, at(20, 9), "c1", "ask", "sms_send", "sms:13800138000", "给 张三 发短信\n全文：我晚到 10 分钟", "HIGH", false),
        AuditEntity(2, at(20, 9), "c1", "reject", "sms_send", "sms:13800138000", "", "HIGH", false),
        AuditEntity(3, at(19, 22), "c2", "run", "web_fetch", "news.example", "[低风险] 标题：…", "LOW", false),
        AuditEntity(4, at(19, 22), "c2", "taint", "web_fetch", "news.example", "读取了外部内容", null, true),
        AuditEntity(5, at(19, 22), "c2", "ask", "sms_send", "sms:139", "…", "HIGH", true),
        AuditEntity(6, at(10, 8), "c3", "error", "calendar_read", "calendar", "[低风险] 没有权限", "LOW", false),
        AuditEntity(7, oct(1, 8), "c4", "deny", "memory_save", "memory", "已被禁用", "MEDIUM", false),
        AuditEntity(8, oct(1, 8), "c4", "approve", "memory_save", "memory", "本对话", "MEDIUM", false),
    )

    @Test fun `filter by tool`() {
        assertEquals(listOf(1L, 2L, 5L), AuditFilter(tool = "sms_send").apply(rows).map { it.id })
    }

    @Test fun `filter by outcome`() {
        assertEquals(listOf(1L, 5L), AuditFilter(outcome = AuditOutcome.ASKED).apply(rows).map { it.id })
        assertEquals(listOf(2L, 7L), AuditFilter(outcome = AuditOutcome.REJECTED).apply(rows).map { it.id })
        assertEquals(listOf(4L, 5L), AuditFilter(outcome = AuditOutcome.TAINT).apply(rows).map { it.id })
        assertEquals(listOf(6L), AuditFilter(outcome = AuditOutcome.FAILED).apply(rows).map { it.id })
        assertEquals(listOf(3L), AuditFilter(outcome = AuditOutcome.RAN).apply(rows).map { it.id })
        assertEquals(listOf(8L), AuditFilter(outcome = AuditOutcome.APPROVED).apply(rows).map { it.id })
    }

    @Test fun `filter by date periods in the user's zone`() {
        assertEquals(listOf(1L, 2L), AuditFilter.of(AuditPeriod.TODAY, now, zone).apply(rows).map { it.id })
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), AuditFilter.of(AuditPeriod.WEEK, now, zone).apply(rows).map { it.id })
        assertEquals(6, AuditFilter.of(AuditPeriod.MONTH, now, zone).apply(rows).size)
        assertEquals(8, AuditFilter.of(AuditPeriod.ALL, now, zone).apply(rows).size)
        assertEquals(listOf(3L, 4L, 5L), AuditFilter.forDay(LocalDate.of(2026, 11, 19), zone).apply(rows).map { it.id })
    }

    @Test fun `period boundaries are local midnight`() {
        val (from, to) = AuditPeriod.TODAY.range(now, zone)
        assertEquals(at(20, 0), from)
        assertEquals(ZonedDateTime.of(2026, 11, 21, 0, 0, 0, 0, zone).toInstant().toEpochMilli(), to)
        assertFalse(AuditFilter(from = from, to = to).matches(rows[2].copy(at = from - 1)))
        assertTrue(AuditFilter(from = from, to = to).matches(rows[2].copy(at = from)))
    }

    @Test fun `filters combine`() {
        val f = AuditFilter.of(AuditPeriod.WEEK, now, zone, tool = "sms_send", outcome = AuditOutcome.TAINT)
        assertEquals(listOf(5L), f.apply(rows).map { it.id })
    }

    @Test fun `export is valid json with every field and the filter`() {
        val f = AuditFilter.of(AuditPeriod.WEEK, now, zone, tool = "sms_send")
        val json = JSONObject(AuditExport.toJson(f.apply(rows), f, now, zone))
        assertEquals(AuditExport.FORMAT, json.getString("format"))
        assertEquals(3, json.getInt("count"))
        assertEquals("sms_send", json.getJSONObject("filter").getString("tool"))
        assertTrue(json.getJSONObject("filter").getString("from").startsWith("2026-11-14T00:00:00+08:00"))
        val e = json.getJSONArray("entries")
        assertEquals(3, e.length())
        val first = e.getJSONObject(0)
        assertEquals("ask", first.getString("kind"))
        assertEquals("sms:13800138000", first.getString("target"))
        assertEquals("HIGH", first.getString("risk"))
        assertEquals("给 张三 发短信\n全文：我晚到 10 分钟", first.getString("detail"))
        assertTrue(first.getString("time").startsWith("2026-11-20T09:00:00+08:00"))
        assertTrue(e.getJSONObject(2).getBoolean("tainted"))
    }

    @Test fun `export of all with null risk`() {
        val json = JSONObject(AuditExport.toJson(rows, AuditFilter(), now, zone))
        assertEquals(8, json.getInt("count"))
        assertTrue(json.getJSONArray("entries").getJSONObject(3).isNull("risk"))
        assertEquals(0, json.getJSONObject("filter").length())
        assertEquals("helium-audit-20261120-1300.json", AuditExport.fileName(now, zone))
    }
}
