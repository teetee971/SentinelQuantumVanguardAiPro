package com.sentinel.quantum.security

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/** Durable safety net for staged outgoing MMS payloads and provider recovery metadata. */
class MmsSendCleanupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {
    override fun doWork(): Result {
        if (inputData.getBoolean(KEY_PROCESS_RESTART, false)) {
            MmsProviderJournal(applicationContext).reconcileReadyAfterProcessDeath()
        }
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
            // One delayed cleanup is sufficient because each new stage prunes old payloads first.
            // REPLACE moves the safety-net deadline to one TTL after the newest staged PDU without
            // accumulating one WorkManager row per MMS.
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun scheduleStartupRecovery(context: Context) {
            val request = OneTimeWorkRequestBuilder<MmsSendCleanupWorker>()
                .setInputData(workDataOf(KEY_PROCESS_RESTART to true))
                .addTag(STARTUP_WORK_TAG)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                STARTUP_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        internal const val WORK_TAG = "sentinel-mms-send-cleanup"
        internal const val STARTUP_WORK_TAG = "sentinel-mms-startup-recovery"
        private const val WORK_NAME = "sentinel-mms-send-cleanup-v1"
        private const val STARTUP_WORK_NAME = "sentinel-mms-startup-recovery-v1"
        private const val KEY_PROCESS_RESTART = "mms.process_restart"
    }
}
