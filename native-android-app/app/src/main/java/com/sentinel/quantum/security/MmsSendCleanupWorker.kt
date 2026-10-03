package com.sentinel.quantum.security

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Durable safety net for staged outgoing MMS payloads and provider recovery metadata. */
class MmsSendCleanupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {
    override fun doWork(): Result {
        MmsSendPduStager.pruneExpired(applicationContext)
        runCatching { MmsConversationStore(applicationContext).repairJournal() }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<MmsSendCleanupWorker>()
                .setInitialDelay(MmsSendPduStager.STAGED_PDU_TTL_MS, TimeUnit.MILLISECONDS)
                .addTag(WORK_TAG)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueue(request)
        }

        internal const val WORK_TAG = "sentinel-mms-send-cleanup"
    }
}
