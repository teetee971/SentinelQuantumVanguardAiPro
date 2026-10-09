package com.sentinel.quantum.security

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters

/** Re-enqueues app-private inbound SMS stages after process death without main-thread disk I/O. */
class IncomingSmsRecoveryWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {
    override fun doWork(): Result {
        val pendingIds = runCatching {
            IncomingSmsDeliveryStore.pendingIds(applicationContext.filesDir)
        }.getOrElse {
            return Result.retry()
        }
        var schedulingFailed = false
        pendingIds.forEach { id ->
            if (runCatching {
                    IncomingSmsDeliveryWorker.schedule(applicationContext, id)
                }.isFailure) {
                schedulingFailed = true
            }
        }
        return if (schedulingFailed) Result.retry() else Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<IncomingSmsRecoveryWorker>()
                .addTag(WORK_TAG)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_WORK,
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        internal const val WORK_TAG = "sentinel-sms-inbound-recovery"
        private const val UNIQUE_WORK = "sentinel-sms-inbound-recovery-v1"
    }
}
