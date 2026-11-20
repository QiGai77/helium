package tech.qigai.helium.glance

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import tech.qigai.helium.HeliumApplication
import java.util.concurrent.TimeUnit

/** Hourly background refresh of weather, feeds, due custom cards and pending "为什么推给你" lines. */
class GlanceRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as HeliumApplication
        return try {
            app.glance.refreshAll(force = false, foreground = false)
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val PERIODIC = "glance-refresh"
        const val NOW = "glance-refresh-now"

        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Unique periodic job; KEEP so app starts don't reset the schedule. */
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<GlanceRefreshWorker>(1, TimeUnit.HOURS)
                .setConstraints(network)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun runOnce(context: Context) {
            val req = OneTimeWorkRequestBuilder<GlanceRefreshWorker>().setConstraints(network).build()
            WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, req)
        }
    }
}
