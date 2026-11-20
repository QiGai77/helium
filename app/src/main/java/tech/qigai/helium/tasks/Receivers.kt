package tech.qigai.helium.tasks

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import tech.qigai.helium.HeliumApplication
import tech.qigai.helium.agent.GrantScope

/** Runs [block] off the main thread while keeping the broadcast alive (goAsync gives us ~10 s). */
private fun BroadcastReceiver.async(context: Context, block: suspend (HeliumApplication) -> Unit) {
    val app = context.applicationContext as HeliumApplication
    val pending = goAsync()
    app.appScope.launch {
        try {
            withTimeoutOrNull(9_000) { block(app) }
        } finally {
            pending.finish()
        }
    }
}

/** The exact (or inexact fallback) alarm for a pending fire. */
class TaskAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TaskScheduler.ACTION_ALARM) return
        val id = intent.getStringExtra(TaskScheduler.EXTRA_TASK) ?: return
        val at = intent.getLongExtra(TaskScheduler.EXTRA_AT, -1L)
        if (at <= 0) return
        async(context) { it.taskEngine.trigger(id, at, fromBackup = false) }
    }
}

/**
 * Alarms do not survive a reboot (WorkManager does). Re-arm everything after boot, an app update, a clock or
 * time-zone change, and when the user grants「闹钟和提醒」(exact alarms) so pending fires switch to exact.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val recompute = intent.action == Intent.ACTION_TIMEZONE_CHANGED || intent.action == Intent.ACTION_TIME_CHANGED
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED", "android.intent.action.QUICKBOOT_POWERON",
            -> async(context) { it.taskEngine.rearmAll(recomputeFuture = recompute) }
        }
    }
}

/** 批准 / 拒绝 buttons on a background approval notification. Always a one-time grant. */
class ApprovalActionReceiver : BroadcastReceiver() {
    companion object {
        const val APPROVE = "tech.qigai.helium.APPROVE"
        const val REJECT = "tech.qigai.helium.REJECT"
        const val EXTRA_REQUEST = "requestId"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_REQUEST) ?: return
        val approve = when (intent.action) { APPROVE -> true; REJECT -> false; else -> return }
        async(context) { app ->
            app.notifications.cancel(HeliumNotifications.approvalNotificationId(id))
            val live = app.approvals.decide(id, approve, if (approve) GrantScope.ONCE else null)
            if (!live) app.notifications.post("这一步已经失效", "后台任务已经结束或超时，没有执行。")
        }
    }
}
