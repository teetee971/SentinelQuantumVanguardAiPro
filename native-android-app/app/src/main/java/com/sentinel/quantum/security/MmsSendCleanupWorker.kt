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
        /**
         * Schedule one cleanup deadline per staged PDU. KEEP is intentional: re-scheduling the same
         * token must never move its deadline later, and a newer MMS gets a different work name.
         */
        fun schedule(context: Context, token: String) {
            val workName = workNameForToken(token)
                ?: throw IllegalArgumentException("invalid MMS cleanup token")
            val request = OneTimeWorkRequestBuilder<MmsSendCleanupWorker>()
                .setInitialDelay(MmsSendPduStager.STAGED_PDU_TTL_MS, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KEY_TOKEN to token))
                .addTag(WORK_TAG)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                workName,
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context, token: String) {
            val workName = workNameForToken(token) ?: return
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(workName)
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

        internal fun workNameForToken(token: String): String? =
            token.takeIf(MmsProviderJournal::validToken)?.let { "$WORK_NAME_PREFIX$it" }

        internal const val WORK_TAG = "sentinel-mms-send-cleanup"
        internal const val STARTUP_WORK_TAG = "sentinel-mms-startup-recovery"
        private const val WORK_NAME_PREFIX = "sentinel-mms-send-cleanup-v2-"
        private const val STARTUP_WORK_NAME = "sentinel-mms-startup-recovery-v1"
        private const val KEY_PROCESS_RESTART = "mms.process_restart"
        private const val KEY_TOKEN = "mms.cleanup.token"
    }
}
