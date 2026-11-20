package tech.qigai.helium.tasks

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import tech.qigai.helium.HeliumApplication

/**
 * WorkManager entry point. MODE_TRIGGER = backup trigger for a pending fire (claims it like the alarm would);
 * MODE_RUN = one headless agent run. Always returns success: retries are scheduled by [TaskEngine] with its own
 * backoff so every attempt and its failure reason lands in the run history.
 */
class TaskWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object {
        const val MODE = "mode"
        const val MODE_TRIGGER = "trigger"
        const val MODE_RUN = "run"
        const val CAUSE = "cause"
        const val ATTEMPT = "attempt"
    }

    private val app get() = applicationContext as HeliumApplication

    override suspend fun doWork(): Result {
        val taskId = inputData.getString(TaskScheduler.EXTRA_TASK) ?: return Result.success()
        val at = inputData.getLong(TaskScheduler.EXTRA_AT, -1L).takeIf { it > 0 }
        when (inputData.getString(MODE)) {
            MODE_TRIGGER -> if (at != null) app.taskEngine.trigger(taskId, at, fromBackup = true)
            MODE_RUN -> app.taskEngine.execute(
                taskId,
                inputData.getString(CAUSE) ?: RunCause.SCHEDULED,
                inputData.getInt(ATTEMPT, 1),
                at,
            )
        }
        return Result.success()
    }

    /** Needed when expedited work runs as a foreground service (API < 31). */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val n = app.notifications.also { it.ensureChannels() }.runningNotification("定时任务")
        // No service type: this path only runs below API 31, where an untyped foreground service is allowed.
        return ForegroundInfo(HeliumNotifications.RUNNING_NOTIFICATION_ID, n)
    }
}
