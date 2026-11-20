package tech.qigai.helium.tasks

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import tech.qigai.helium.MainActivity
import tech.qigai.helium.R
import tech.qigai.helium.agent.tools.Notifier

/**
 * All of Helium's notifications: reminders (high importance, exact alarm), task results, approvals needed by a
 * background run (with 批准 / 拒绝 actions), and the plain `notify` tool. Tapping one opens the app on [ROUTE_EXTRA].
 */
class HeliumNotifications(private val context: Context) : Notifier {
    companion object {
        const val CH_REMINDERS = "reminders"
        const val CH_TASKS = "task_results"
        const val CH_APPROVALS = "approvals"
        const val CH_GENERAL = "general"
        const val CH_RUNNING = "task_running"
        const val ROUTE_EXTRA = "helium.route"

        fun taskNotificationId(taskId: String) = 0x10000 + (taskId.hashCode() and 0xFFFF)
        fun approvalNotificationId(requestId: String) = 0x20000 + (requestId.hashCode() and 0xFFFF)
        const val RUNNING_NOTIFICATION_ID = 0x30001
    }

    private val nm = NotificationManagerCompat.from(context)
    @Volatile private var channelsReady = false

    fun ensureChannels() {
        if (channelsReady || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val sys = context.getSystemService(NotificationManager::class.java) ?: return
        sys.createNotificationChannels(
            listOf(
                NotificationChannel(CH_REMINDERS, "提醒", NotificationManager.IMPORTANCE_HIGH).apply { description = "你让 Helium 设的提醒，到点准时弹出" },
                NotificationChannel(CH_TASKS, "定时任务结果", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "早报、总结等定时任务的运行结果" },
                NotificationChannel(CH_APPROVALS, "待你确认", NotificationManager.IMPORTANCE_HIGH).apply { description = "后台任务需要你批准的操作，超时视为拒绝" },
                NotificationChannel(CH_GENERAL, "Helium 通知", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CH_RUNNING, "任务运行中", NotificationManager.IMPORTANCE_MIN).apply { setShowBadge(false) },
            ),
        )
        channelsReady = true
    }

    override fun canNotify(): Boolean {
        val permitted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return permitted && nm.areNotificationsEnabled()
    }

    override fun post(title: String, body: String): Boolean =
        show((System.currentTimeMillis() and 0xFFFF).toInt() + 0x40000, CH_GENERAL, title, body, route = null)

    fun reminder(taskId: String, title: String, text: String): Boolean =
        show(taskNotificationId(taskId), CH_REMINDERS, title, text.takeIf { it != title } ?: "到时间了", "task/$taskId") {
            setCategory(NotificationCompat.CATEGORY_REMINDER).setPriority(NotificationCompat.PRIORITY_HIGH)
        }

    fun taskResult(taskId: String, title: String, text: String, failed: Boolean): Boolean =
        show(taskNotificationId(taskId), CH_TASKS, if (failed) "$title · 运行失败" else title, text, "task/$taskId") {
            setCategory(if (failed) NotificationCompat.CATEGORY_ERROR else NotificationCompat.CATEGORY_STATUS)
        }

    fun approval(requestId: String, taskTitle: String, title: String, detail: String, timeoutMin: Long): Boolean {
        val approve = actionIntent(requestId, true)
        val reject = actionIntent(requestId, false)
        return show(approvalNotificationId(requestId), CH_APPROVALS, "「$taskTitle」需要你确认", "$title\n$detail\n\n$timeoutMin 分钟内不处理视为拒绝。", "approval") {
            setCategory(NotificationCompat.CATEGORY_REMINDER).setPriority(NotificationCompat.PRIORITY_HIGH)
                .setTimeoutAfter(timeoutMin * 60_000)
                .addAction(0, "拒绝", reject)
                .addAction(0, "批准（仅这一次）", approve)
        }
    }

    fun runningNotification(title: String) = NotificationCompat.Builder(context, CH_RUNNING)
        .setSmallIcon(R.drawable.ic_stat_helium)
        .setContentTitle("正在运行：$title")
        .setOngoing(true)
        .setSilent(true)
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .build()

    fun cancel(id: Int) = nm.cancel(id)

    private fun actionIntent(requestId: String, approve: Boolean): PendingIntent {
        val i = Intent(context, ApprovalActionReceiver::class.java)
            .setAction(if (approve) ApprovalActionReceiver.APPROVE else ApprovalActionReceiver.REJECT)
            .putExtra(ApprovalActionReceiver.EXTRA_REQUEST, requestId)
        return PendingIntent.getBroadcast(context, (requestId + approve).hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun openIntent(route: String?, code: Int): PendingIntent {
        val i = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .apply { if (route != null) putExtra(ROUTE_EXTRA, route) }
        return PendingIntent.getActivity(context, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    @SuppressLint("MissingPermission")
    private fun show(id: Int, channel: String, title: String, text: String, route: String?, extra: NotificationCompat.Builder.() -> Unit = {}): Boolean {
        ensureChannels()
        if (!canNotify()) return false
        val b = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_helium)
            .setContentTitle(title)
            .setContentText(text.lineSequence().firstOrNull().orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openIntent(route, id))
            .setAutoCancel(true)
        b.extra()
        return runCatching { nm.notify(id, b.build()); true }.getOrDefault(false)
    }
}
