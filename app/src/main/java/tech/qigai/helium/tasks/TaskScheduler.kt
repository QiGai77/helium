package tech.qigai.helium.tasks

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/**
 * Two triggers per pending fire, one winner:
 * 1. an AlarmManager alarm at the exact time (setExactAndAllowWhileIdle when SCHEDULE_EXACT_ALARM is granted,
 *    setAndAllowWhileIdle otherwise) → [TaskAlarmReceiver];
 * 2. a WorkManager backup a few minutes later (survives alarm loss; WorkManager persists across reboots).
 * Both call [TaskEngine.trigger], which claims `nextRunAt` with a compare-and-set, so only one of them runs it.
 * The agent run itself is always a WorkManager job ([TaskWorker]) so it gets a proper background budget.
 */
class TaskScheduler(private val context: Context) {
    companion object {
        const val ACTION_ALARM = "tech.qigai.helium.TASK_ALARM"
        const val EXTRA_TASK = "taskId"
        const val EXTRA_AT = "scheduledFor"
        const val BACKUP_DELAY_MS = 5 * 60_000L

        fun runName(id: String) = "task-run-$id"
        fun retryName(id: String) = "task-retry-$id"
        fun backupName(id: String) = "task-backup-$id"
    }

    private val am = context.getSystemService(AlarmManager::class.java)
    private val wm get() = WorkManager.getInstance(context)

    fun exactAllowed(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am?.canScheduleExactAlarms() == true

    /**
     * When exact alarms are not granted, the reminder may fire minutes late. This is the single
     * source for that downgrade: the text the task screen banner and the agent tools show, and the
     * condition under which they show it. The banner itself lives in TasksScreen (wired to
     * [exactAllowed] in the nav host) with a button that opens the system "Alarms & reminders" page.
     */
    object ExactAlarmNotice {
        const val MESSAGE = "精确闹钟未授权，提醒可能不准时（有几分钟误差）。请去系统设置开启「闹钟和提醒」权限。"
        fun show(exactAllowed: Boolean, hasEnabledTask: Boolean): Boolean = !exactAllowed && hasEnabledTask
        fun message(exactAllowed: Boolean, hasEnabledTask: Boolean): String? =
            if (show(exactAllowed, hasEnabledTask)) MESSAGE else null
    }

    private fun alarmIntent(taskId: String, at: Long, flags: Int): PendingIntent? {
        val i = Intent(context, TaskAlarmReceiver::class.java)
            .setAction(ACTION_ALARM)
            .setData(Uri.parse("helium://task/$taskId"))
            .putExtra(EXTRA_TASK, taskId)
            .putExtra(EXTRA_AT, at)
        return PendingIntent.getBroadcast(context, 0, i, flags or PendingIntent.FLAG_IMMUTABLE)
    }

    /**
     * (Re)arms both triggers for [at]; replaces whatever was armed for this task before.
     * Returns true when the alarm is exact; false when exact alarms are not granted and the alarm
     * degraded to an inexact one (the reminder may then fire minutes late — see [ExactAlarmNotice]).
     */
    fun arm(taskId: String, at: Long): Boolean {
        val pi = alarmIntent(taskId, at, PendingIntent.FLAG_UPDATE_CURRENT) ?: return false
        val manager = am
        var exact = false
        if (manager != null) {
            try {
                if (exactAllowed()) {
                    manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                    exact = true
                } else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } catch (e: SecurityException) {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        }
        val delay = (at - System.currentTimeMillis() + BACKUP_DELAY_MS).coerceAtLeast(0)
        val backup = OneTimeWorkRequestBuilder<TaskWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(TaskWorker.MODE to TaskWorker.MODE_TRIGGER, EXTRA_TASK to taskId, EXTRA_AT to at))
            .addTag("helium-task").addTag("task:$taskId")
            .build()
        wm.enqueueUniqueWork(backupName(taskId), ExistingWorkPolicy.REPLACE, backup)
        return exact
    }

    /** Cancels the alarm, the backup and any pending retry. A run already in progress finishes. */
    fun disarm(taskId: String) {
        alarmIntent(taskId, 0, PendingIntent.FLAG_NO_CREATE)?.let { pi -> am?.cancel(pi); pi.cancel() }
        wm.cancelUniqueWork(backupName(taskId))
        wm.cancelUniqueWork(retryName(taskId))
    }

    /** Starts a run now (expedited when quota allows). KEEP: a second request while one is queued/running is dropped. */
    fun enqueueRun(taskId: String, cause: String, attempt: Int, scheduledFor: Long?) {
        val req = OneTimeWorkRequestBuilder<TaskWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(runData(taskId, cause, attempt, scheduledFor))
            .addTag("helium-task").addTag("task:$taskId")
            .build()
        wm.enqueueUniqueWork(runName(taskId), ExistingWorkPolicy.KEEP, req)
    }

    /** Retry after the backoff delay, and only once the phone is online. */
    fun enqueueRetry(taskId: String, attempt: Int, scheduledFor: Long?, delayMs: Long) {
        val req = OneTimeWorkRequestBuilder<TaskWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(runData(taskId, RunCause.RETRY, attempt, scheduledFor))
            .addTag("helium-task").addTag("task:$taskId")
            .build()
        wm.enqueueUniqueWork(retryName(taskId), ExistingWorkPolicy.REPLACE, req)
    }

    fun cancelAll(taskId: String) {
        disarm(taskId)
        wm.cancelUniqueWork(runName(taskId))
    }

    private fun runData(taskId: String, cause: String, attempt: Int, scheduledFor: Long?) = workDataOf(
        TaskWorker.MODE to TaskWorker.MODE_RUN,
        EXTRA_TASK to taskId,
        TaskWorker.CAUSE to cause,
        TaskWorker.ATTEMPT to attempt,
        EXTRA_AT to (scheduledFor ?: -1L),
    )
}
