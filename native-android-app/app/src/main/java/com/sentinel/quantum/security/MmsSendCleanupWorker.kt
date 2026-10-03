package com.sentinel.quantum.security

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/** Durable safety net for staged MMS payloads and outgoing/incoming provider recovery metadata. */
class MmsSendCleanupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {
    override fun doWork(): Result {
        inputData.getString(KEY_FILE_NAME)?.let { fileName ->
            MmsSendPduStager.expire(applicationContext, fileName)
        }
        if (inputData.getBoolean(KEY_PROCESS_RESTART, false)) {
            MmsProviderJournal(applicationContext).reconcileReadyAfterProcessDeath()
            MmsSendPduStager.pruneExpired(applicationContext)
            runCatching { MmsConversationStore(applicationContext).repairJournal() }
            runCatching { IncomingMmsConversationStore(applicationContext).repairJournal() }
            // WorkManager survives process death, but an explicit startup nudge removes the delay
            // for a downloaded PDU whose Android callback was lost with the dead process.
            runCatching { MmsDownloadRecoveryWorker.schedulePendingNow(applicationContext) }
        }
        return Result.success()
    }

    companion object {
        /**
         * Schedule one durable deadline per staged PDU.
         *
         * A single REPLACE-able cleanup deadline is incorrect here: a newer MMS could postpone an
         * older PDU's cleanup. Per-file unique work keeps each payload tied to its own staging TTL
         * while callback-driven cleanup cancels the pending work when Android finishes earlier.
         */
        fun schedule(context: Context, fileName: String) {
            require(MmsSendPduStager.isValidStagedFileName(fileName)) { "invalid MMS staged file" }
            val request = OneTimeWorkRequestBuilder<MmsSendCleanupWorker>()
                .setInitialDelay(MmsSendPduStager.STAGED_PDU_TTL_MS, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KEY_FILE_NAME to fileName))
                .addTag(WORK_TAG)
                .addTag(fileTag(fileName))
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                workName(fileName),
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context, fileName: String) {
            if (!MmsSendPduStager.isValidStagedFileName(fileName)) return
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(workName(fileName))
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
        private const val WORK_PREFIX = "sentinel-mms-send-cleanup-v2-"
        private const val FILE_TAG_PREFIX = "sentinel-mms-send-file-"
        private const val STARTUP_WORK_NAME = "sentinel-mms-startup-recovery-v1"
        private const val KEY_PROCESS_RESTART = "mms.process_restart"
        private const val KEY_FILE_NAME = "mms.file_name"

        private fun workName(fileName: String) = WORK_PREFIX + fileName.removeSuffix(".pdu")
        private fun fileTag(fileName: String) = FILE_TAG_PREFIX + fileName.removeSuffix(".pdu")
    }
}
