package tech.qigai.helium.tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** M5: when exact alarms are not granted, the downgrade must be explicit, not silent. */
class ExactAlarmNoticeTest {
    @Test fun `notice shows only when alarms are inexact and a task is enabled`() {
        assertTrue(TaskScheduler.ExactAlarmNotice.show(exactAllowed = false, hasEnabledTask = true))
        assertFalse(TaskScheduler.ExactAlarmNotice.show(exactAllowed = true, hasEnabledTask = true))
        assertFalse(TaskScheduler.ExactAlarmNotice.show(exactAllowed = false, hasEnabledTask = false))
        assertFalse(TaskScheduler.ExactAlarmNotice.show(exactAllowed = true, hasEnabledTask = false))
    }

    @Test fun `notice message names the problem and the fix`() {
        val m = TaskScheduler.ExactAlarmNotice.message(exactAllowed = false, hasEnabledTask = true)!!
        assertTrue(m, m.contains("精确闹钟未授权") && m.contains("可能不准时"))
        assertTrue(m, m.contains("系统设置") && m.contains("闹钟和提醒"))
        assertNull(TaskScheduler.ExactAlarmNotice.message(exactAllowed = true, hasEnabledTask = true))
        assertNull(TaskScheduler.ExactAlarmNotice.message(exactAllowed = false, hasEnabledTask = false))
    }

    @Test fun `exactNote discloses the downgrade to the agent tools`() {
        val denied = object : TaskService by FakeDeniedTasks {
            override fun exactAlarms() = false
        }
        assertTrue(exactNote(denied).contains("精确闹钟未授权"))
        assertTrue(exactNote(denied).contains("任务页"))
        val allowed = object : TaskService by FakeDeniedTasks {
            override fun exactAlarms() = true
        }
        assertEquals("", exactNote(allowed))
    }
}

/** Minimal TaskService stub; only exactAlarms() matters here. */
private object FakeDeniedTasks : TaskService {
    override suspend fun create(draft: TaskDraft): TaskInfo = error("unused")
    override suspend fun list(): List<TaskInfo> = emptyList()
    override suspend fun get(id: String): TaskInfo? = null
    override suspend fun pause(id: String): TaskInfo? = null
    override suspend fun resume(id: String): TaskInfo? = null
    override suspend fun skipNext(id: String): TaskInfo? = null
    override suspend fun runNow(id: String): Boolean = false
    override suspend fun delete(id: String): Boolean = false
    override suspend fun edit(id: String, patch: TaskPatch): TaskInfo? = null
    override fun exactAlarms(): Boolean = false
}
