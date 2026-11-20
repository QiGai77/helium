package tech.qigai.helium.tasks

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.qigai.helium.agent.Risk
import tech.qigai.helium.agent.tools.ToolContext
import java.time.ZoneId
import java.time.ZonedDateTime

private val SH = ZoneId.of("Asia/Shanghai")
private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) = ZonedDateTime.of(y, mo, d, h, mi, 0, 0, SH).toInstant().toEpochMilli()
private val NOW = at(2026, 11, 20, 12, 37) // Friday
private fun <T> ok(p: Parsed<T>): T = (p as? Parsed.Ok)?.value ?: error("expected Ok, got $p")
private fun err(p: Parsed<*>): String = (p as? Parsed.Err)?.message ?: error("expected Err, got $p")

class TaskArgsTest {
    @Test fun times() {
        assertEquals(8 to 0, TaskArgs.parseTime("08:00"))
        assertEquals(8 to 5, TaskArgs.parseTime("8:5"))
        assertEquals(20 to 30, TaskArgs.parseTime("20：30"))
        assertEquals(8 to 0, TaskArgs.parseTime("8点"))
        assertEquals(8 to 30, TaskArgs.parseTime("早上8点半"))
        assertEquals(15 to 0, TaskArgs.parseTime("下午3点"))
        assertEquals(21 to 15, TaskArgs.parseTime("晚上9点15分"))
        assertNull(TaskArgs.parseTime("25:00"))
        assertNull(TaskArgs.parseTime("soon"))
        assertNull(TaskArgs.parseTime(""))
    }

    @Test fun weekdays() {
        assertEquals(Weekdays.of(1, 3, 5), TaskArgs.parseWeekdays(JSONArray(listOf(1, 3, 5))))
        assertEquals(Weekdays.of(1, 3, 5), TaskArgs.parseWeekdays("一、三、五"))
        assertEquals(Weekdays.of(1, 3, 5), TaskArgs.parseWeekdays("周一,周三,周五"))
        assertEquals(Weekdays.of(7), TaskArgs.parseWeekdays(JSONArray(listOf("周日"))))
        assertEquals(Weekdays.WORKDAYS, TaskArgs.parseWeekdays("工作日"))
        assertEquals(Weekdays.WORKDAYS, TaskArgs.parseWeekdays("周一到周五"))
        assertEquals(Weekdays.WEEKEND, TaskArgs.parseWeekdays("weekend"))
        assertEquals(Weekdays.of(2, 4), TaskArgs.parseWeekdays("tue,thu"))
        assertEquals(0, TaskArgs.parseWeekdays(JSONArray(listOf(0, 9))))
        assertEquals(0, TaskArgs.parseWeekdays(null))
    }

    @Test fun createDaily() {
        val d = ok(TaskArgs.parseCreate(JSONObject("""{"title":"早报","prompt":"天气+日历","kind":"daily","time":"08:00"}"""), NOW, SH))
        assertEquals(TaskAction.AGENT, d.action)
        assertEquals(ScheduleSpec(TaskKind.DAILY, 8, 0, Weekdays.ALL), d.spec)
        assertEquals("早报", d.title)
    }

    @Test fun createWeeklyFromNames() {
        val d = ok(TaskArgs.parseCreate(JSONObject("""{"title":"周报","prompt":"总结","kind":"weekly","time":"17:00","weekdays":"周五"}"""), NOW, SH))
        assertEquals(ScheduleSpec(TaskKind.WEEKLY, 17, 0, Weekdays.of(5)), d.spec)
    }

    @Test fun weeklyAllDaysBecomesDaily() {
        val d = ok(TaskArgs.parseCreate(JSONObject("""{"prompt":"x","kind":"weekly","time":"07:00","weekdays":[1,2,3,4,5,6,7]}"""), NOW, SH))
        assertEquals(TaskKind.DAILY, d.spec.kind)
    }

    @Test fun kindInferredFromWeekdays() {
        val d = ok(TaskArgs.parseCreate(JSONObject("""{"prompt":"x","time":"07:00","weekdays":[6,7]}"""), NOW, SH))
        assertEquals(ScheduleSpec(TaskKind.WEEKLY, 7, 0, Weekdays.WEEKEND), d.spec)
    }

    @Test fun createErrors() {
        assertTrue(err(TaskArgs.parseCreate(JSONObject("""{"kind":"daily","time":"08:00"}"""), NOW, SH)).contains("prompt"))
        assertTrue(err(TaskArgs.parseCreate(JSONObject("""{"prompt":"x","kind":"hourly","time":"08:00"}"""), NOW, SH)).contains("kind"))
        assertTrue(err(TaskArgs.parseCreate(JSONObject("""{"prompt":"x","kind":"daily"}"""), NOW, SH)).contains("time"))
        assertTrue(err(TaskArgs.parseCreate(JSONObject("""{"prompt":"x","kind":"daily","time":"8am"}"""), NOW, SH)).contains("HH:mm"))
        assertTrue(err(TaskArgs.parseCreate(JSONObject("""{"prompt":"x","kind":"weekly","time":"08:00"}"""), NOW, SH)).contains("weekdays"))
        assertTrue(err(TaskArgs.parseCreate(JSONObject("""{"prompt":"x","kind":"once","time":"08:00","date":"2026-11-19"}"""), NOW, SH)).contains("已经过去"))
    }

    @Test fun onceWithoutDateTakesNextOccurrence() {
        val later = ok(TaskArgs.parseReminder(JSONObject("""{"text":"开会","kind":"once","time":"15:00"}"""), NOW, SH))
        assertEquals(at(2026, 11, 20, 15, 0), later.spec.onceAt)
        val earlier = ok(TaskArgs.parseReminder(JSONObject("""{"text":"跑步","kind":"once","time":"07:00"}"""), NOW, SH))
        assertEquals(at(2026, 11, 21, 7, 0), earlier.spec.onceAt)
    }

    @Test fun onceWithDateWords() {
        val d = ok(TaskArgs.parseReminder(JSONObject("""{"text":"交房租","time":"09:00","date":"明天"}"""), NOW, SH))
        assertEquals(TaskKind.ONCE, d.spec.kind)
        assertEquals(at(2026, 11, 21, 9, 0), d.spec.onceAt)
        val e = ok(TaskArgs.parseReminder(JSONObject("""{"text":"x","kind":"once","time":"09:00","date":"12月1日"}"""), NOW, SH))
        assertEquals(at(2026, 12, 1, 9, 0), e.spec.onceAt)
    }

    @Test fun inMinutes() {
        val d = ok(TaskArgs.parseReminder(JSONObject("""{"text":"关火","in_minutes":10}"""), NOW, SH))
        assertEquals(TaskKind.ONCE, d.spec.kind)
        assertEquals(NOW + 600_000, d.spec.onceAt)
        assertEquals("关火", d.prompt)
        assertEquals(TaskAction.NOTIFY, d.action)
        assertTrue(err(TaskArgs.parseReminder(JSONObject("""{"text":"x","in_minutes":-3}"""), NOW, SH)).contains("in_minutes"))
    }

    @Test fun drinkWaterEveryMorning() {
        val d = ok(TaskArgs.parseReminder(JSONObject("""{"text":"喝水","kind":"daily","time":"8:00"}"""), NOW, SH))
        assertEquals(ScheduleSpec(TaskKind.DAILY, 8, 0), d.spec)
        assertEquals(at(2026, 11, 21, 8, 0), Schedule.nextFire(d.spec, NOW, SH))
    }

    @Test fun updates() {
        assertEquals(UpdateOp.PAUSE, ok(TaskArgs.parseUpdate(JSONObject("""{"id":"a","action":"pause"}"""), NOW, SH)).op)
        assertEquals(UpdateOp.SKIP_NEXT, ok(TaskArgs.parseUpdate(JSONObject("""{"id":"a","action":"skip-next"}"""), NOW, SH)).op)
        val e = ok(TaskArgs.parseUpdate(JSONObject("""{"id":"a","action":"edit","time":"09:30"}"""), NOW, SH))
        assertEquals(TaskPatch(hour = 9, minute = 30), e.patch)
        val w = ok(TaskArgs.parseUpdate(JSONObject("""{"id":"a","action":"edit","kind":"weekly","time":"09:30","weekdays":[2]}"""), NOW, SH))
        assertEquals(TaskKind.WEEKLY, w.patch.kind); assertEquals(Weekdays.of(2), w.patch.weekdays)
        assertTrue(err(TaskArgs.parseUpdate(JSONObject("""{"id":"a","action":"edit"}"""), NOW, SH)).contains("至少"))
        assertTrue(err(TaskArgs.parseUpdate(JSONObject("""{"action":"pause"}"""), NOW, SH)).contains("id"))
        assertTrue(err(TaskArgs.parseUpdate(JSONObject("""{"id":"a","action":"explode"}"""), NOW, SH)).contains("action"))
    }
}

/** In-memory TaskService to exercise the tools end to end (parsing → service → observation). */
private class FakeTasks(private val now: Long) : TaskService {
    val items = LinkedHashMap<String, TaskInfo>()
    var exact = true
    var running = mutableSetOf<String>()
    private var n = 0
    override suspend fun create(draft: TaskDraft): TaskInfo {
        val id = "task-%04d-abcdef".format(++n)
        val t = TaskInfo(id, draft.title, draft.prompt, draft.action, draft.spec, true, Schedule.nextFire(draft.spec, now, SH), null, null, 0, "task-$id", now)
        items[id] = t; return t
    }
    override suspend fun list() = items.values.toList()
    override suspend fun get(id: String) = items[id]
    override suspend fun pause(id: String) = items[id]?.copy(enabled = false)?.also { items[id] = it }
    override suspend fun resume(id: String) = items[id]?.let { it.copy(enabled = true, nextRunAt = Schedule.nextFire(it.spec, now, SH)) }?.also { items[id] = it }
    override suspend fun skipNext(id: String) = items[id]?.let { t -> t.copy(nextRunAt = t.nextRunAt?.let { Schedule.nextFire(t.spec, it, SH) }) }?.also { items[id] = it }
    override suspend fun runNow(id: String) = running.add(id)
    override suspend fun delete(id: String) = items.remove(id) != null
    override suspend fun edit(id: String, patch: TaskPatch) = items[id]?.let { it.copy(title = patch.title ?: it.title) }?.also { items[id] = it }
    override fun exactAlarms() = exact
}

class TaskToolsTest {
    private val ctx = ToolContext("c1")

    @Test fun reminderCreatesNotifyTask() = runBlocking {
        val svc = FakeTasks(NOW)
        val out = ReminderCreateTool(svc, { SH }, { NOW }).run(JSONObject("""{"text":"喝水","kind":"daily","time":"08:00"}"""), ctx)
        assertFalse(out.text, out.isError)
        val t = svc.items.values.single()
        assertEquals(TaskAction.NOTIFY, t.action)
        assertEquals(at(2026, 11, 21, 8, 0), t.nextRunAt)
        assertTrue(out.text, out.text.contains("每天 08:00") && out.text.contains("11月21日"))
    }

    @Test fun inexactAlarmIsDisclosed() = runBlocking {
        val svc = FakeTasks(NOW).apply { exact = false }
        val out = ReminderCreateTool(svc, { SH }, { NOW }).run(JSONObject("""{"text":"喝水","in_minutes":5}"""), ctx)
        assertTrue(out.text.contains("误差"))
    }

    @Test fun createErrorGoesBackToModel() = runBlocking {
        val svc = FakeTasks(NOW)
        val out = TaskCreateTool(svc, { SH }, { NOW }).run(JSONObject("""{"title":"早报","kind":"daily","time":"08:00"}"""), ctx)
        assertTrue(out.isError); assertTrue(svc.items.isEmpty())
    }

    @Test fun listAndUpdateByPrefix() = runBlocking {
        val svc = FakeTasks(NOW)
        TaskCreateTool(svc, { SH }, { NOW }).run(JSONObject("""{"title":"早报","prompt":"天气","kind":"daily","time":"08:00"}"""), ctx)
        val list = JSONArray(TaskListTool(svc, { SH }, { NOW }).run(JSONObject(), ctx).text)
        assertEquals("早报", list.getJSONObject(0).getString("title"))
        val upd = TaskUpdateTool(svc, { SH }, { NOW })
        val paused = upd.run(JSONObject("""{"id":"task-0001","action":"pause"}"""), ctx)
        assertTrue(paused.text, paused.text.contains("已暂停"))
        assertFalse(svc.items.values.single().enabled)
        val missing = upd.run(JSONObject("""{"id":"nope","action":"pause"}"""), ctx)
        assertTrue(missing.isError)
        val skip = upd.run(JSONObject("""{"id":"task-0001-abcdef","action":"resume"}"""), ctx)
        assertFalse(skip.isError)
        upd.run(JSONObject("""{"id":"task-0001-abcdef","action":"skip_next"}"""), ctx)
        assertEquals(at(2026, 11, 22, 8, 0), svc.items.values.single().nextRunAt)
    }

    @Test fun riskLevels() {
        val svc = FakeTasks(NOW)
        val upd = TaskUpdateTool(svc)
        assertEquals(Risk.HIGH, upd.risk(JSONObject("""{"id":"a","action":"delete"}""")))
        assertEquals(Risk.MEDIUM, upd.risk(JSONObject("""{"id":"a","action":"edit"}""")))
        assertEquals(Risk.MEDIUM, upd.risk(JSONObject("""{"id":"a","action":"run_now"}""")))
        assertEquals(Risk.LOW, upd.risk(JSONObject("""{"id":"a","action":"pause"}""")))
        assertEquals(Risk.MEDIUM, TaskCreateTool(svc).risk(JSONObject()))
        assertEquals(Risk.LOW, ReminderCreateTool(svc).risk(JSONObject()))
        assertEquals(Risk.LOW, TaskListTool(svc).risk(JSONObject()))
    }
}
