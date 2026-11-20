package tech.qigai.helium.tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ScheduleTest {
    private val sh = ZoneId.of("Asia/Shanghai")
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int = 0) = ZonedDateTime.of(y, mo, d, h, mi, s, 0, sh).toInstant().toEpochMilli()
    private fun show(ms: Long?) = ms?.let { java.time.Instant.ofEpochMilli(it).atZone(sh).toLocalDateTime().toString() }

    @Test fun dailyLaterToday() {
        // Friday 2026-11-20 07:30 → today 08:00
        assertEquals(at(2026, 11, 20, 8, 0), Schedule.nextFire(ScheduleSpec(TaskKind.DAILY, 8, 0), at(2026, 11, 20, 7, 30), sh))
    }

    @Test fun dailyAlreadyPassedGoesToTomorrow() {
        assertEquals(at(2026, 11, 21, 8, 0), Schedule.nextFire(ScheduleSpec(TaskKind.DAILY, 8, 0), at(2026, 11, 20, 12, 37), sh))
    }

    @Test fun dailyExactlyAtFireTimeIsStrictlyAfter() {
        val fire = at(2026, 11, 20, 8, 0)
        assertEquals(at(2026, 11, 21, 8, 0), Schedule.nextFire(ScheduleSpec(TaskKind.DAILY, 8, 0), fire, sh))
    }

    @Test fun dailyAcrossMonthAndYearEnd() {
        assertEquals(at(2027, 1, 1, 0, 5), Schedule.nextFire(ScheduleSpec(TaskKind.DAILY, 0, 5), at(2026, 12, 31, 23, 59), sh))
    }

    @Test fun dailyIsWallClockInZoneNotUtc() {
        // 08:00 Shanghai is 00:00 UTC; just after 23:59 UTC on the 20th is 07:59 Shanghai on the 21st.
        val after = ZonedDateTime.of(2026, 11, 20, 23, 59, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()
        assertEquals(at(2026, 11, 21, 8, 0), Schedule.nextFire(ScheduleSpec(TaskKind.DAILY, 8, 0), after, sh))
    }

    @Test fun weeklyPicksNextSelectedWeekday() {
        // 2026-11-20 is a Friday. Mon/Wed 09:00 → Monday 2026-11-23.
        val spec = ScheduleSpec(TaskKind.WEEKLY, 9, 0, Weekdays.of(1, 3))
        assertEquals(show(at(2026, 11, 23, 9, 0)), show(Schedule.nextFire(spec, at(2026, 11, 20, 12, 0), sh)))
    }

    @Test fun weeklyTodayIfStillAhead() {
        val spec = ScheduleSpec(TaskKind.WEEKLY, 18, 30, Weekdays.of(5))
        assertEquals(at(2026, 11, 20, 18, 30), Schedule.nextFire(spec, at(2026, 11, 20, 12, 0), sh))
    }

    @Test fun weeklySameWeekdayPassedIsNextWeek() {
        val spec = ScheduleSpec(TaskKind.WEEKLY, 8, 0, Weekdays.of(5))
        assertEquals(at(2026, 11, 27, 8, 0), Schedule.nextFire(spec, at(2026, 11, 20, 8, 0), sh))
    }

    @Test fun weeklySundayBit() {
        val spec = ScheduleSpec(TaskKind.WEEKLY, 10, 0, Weekdays.of(7))
        assertEquals(at(2026, 11, 22, 10, 0), Schedule.nextFire(spec, at(2026, 11, 20, 12, 0), sh))
    }

    @Test fun weeklyWorkdaysFromFridayEvening() {
        val spec = ScheduleSpec(TaskKind.WEEKLY, 8, 0, Weekdays.WORKDAYS)
        assertEquals(at(2026, 11, 23, 8, 0), Schedule.nextFire(spec, at(2026, 11, 20, 20, 0), sh))
    }

    @Test fun weeklyWithoutDaysNeverFires() {
        assertNull(Schedule.nextFire(ScheduleSpec(TaskKind.WEEKLY, 8, 0, 0), at(2026, 11, 20, 8, 0), sh))
    }

    @Test fun onceFiresOnlyInFuture() {
        val t = at(2026, 11, 21, 9, 0)
        assertEquals(t, Schedule.nextFire(ScheduleSpec(TaskKind.ONCE, 9, 0, onceAt = t), at(2026, 11, 20, 9, 0), sh))
        assertNull(Schedule.nextFire(ScheduleSpec(TaskKind.ONCE, 9, 0, onceAt = t), t, sh))
    }

    @Test fun consecutiveDailyFiresAreOneDayApart() {
        var t = at(2026, 11, 20, 12, 0)
        val spec = ScheduleSpec(TaskKind.DAILY, 8, 0)
        repeat(3) {
            val n = Schedule.nextFire(spec, t, sh)!!
            if (it > 0) assertEquals(86_400_000L, n - t)
            t = n
        }
    }

    @Test fun describe() {
        assertEquals("每天 08:00", Schedule.describe(ScheduleSpec(TaskKind.DAILY, 8, 0), sh))
        assertEquals("工作日 07:05", Schedule.describe(ScheduleSpec(TaskKind.WEEKLY, 7, 5, Weekdays.WORKDAYS), sh))
        assertEquals("每周一、三 20:30", Schedule.describe(ScheduleSpec(TaskKind.WEEKLY, 20, 30, Weekdays.of(1, 3)), sh))
        assertTrue(Schedule.formatAt(at(2026, 11, 21, 8, 0), sh, at(2026, 11, 20, 8, 0)).startsWith("11月21日 周六 08:00"))
    }
}

class BackoffTest {
    @Test fun exponentialMinutes() {
        assertEquals(listOf(60_000L, 120_000L, 240_000L, 480_000L, 960_000L), (1..5).map { Backoff.delayMs(it) })
    }

    @Test fun capped() {
        assertEquals(Backoff.MAX_MS, Backoff.delayMs(30))
        assertEquals(Backoff.MAX_MS, Backoff.delayMs(1000))
        assertEquals(0L, Backoff.delayMs(0))
    }

    @Test fun givesUpAfterBudget() {
        assertEquals(1_000L + 60_000L, Backoff.nextRetryAt(1, 1_000L))
        assertTrue(Backoff.nextRetryAt(Backoff.MAX_RETRIES, 0L) != null)
        assertNull(Backoff.nextRetryAt(Backoff.MAX_RETRIES + 1, 0L))
    }

    @Test fun labels() {
        assertEquals("1 分钟", Backoff.label(60_000))
        assertEquals("2 小时", Backoff.label(2 * 3600_000L))
    }

    @Test fun failureReasons() {
        assertTrue(FailureReason.classify("Unable to resolve host \"api.deepseek.com\": No address associated with hostname").startsWith("网络不可用"))
        assertTrue(FailureReason.classify("HTTP 401 Unauthorized").startsWith("模型 API Key"))
        assertEquals("未知错误", FailureReason.classify(null))
    }
}
