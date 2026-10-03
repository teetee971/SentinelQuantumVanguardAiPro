package com.sentinel.quantum.security

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/**
 * Durable per-file deadline for temporary MMS downloads.
 *
 * Before the final bounded deletion, one last recovery pass is attempted. This preserves the
 * original privacy guarantee (temporary cache cannot live forever) without confusing cleanup with
 * product recovery when Android's callback was lost.
 */
class MmsDownloadCleanupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {
    override fun doWork(): Result {
        val fileName = inputData.getString(KEY_FILE_NAME) ?: return Result.success()
        runCatching {
            MmsDownloadRecovery.recover(
                context = applicationContext,
                fileName = fileName,
                allowQuarantine = true
            )
        }
        MmsDownloadCoordinator.expire(applicationContext, fileName)
        return Result.success()
    }

    companion object {
        fun schedule(context: Context, fileName: String) {
            require(MmsDownloadCoordinator.isValidStagedFileName(fileName)) {
                "invalid MMS download file"
            }
            val request = OneTimeWorkRequestBuilder<MmsDownloadCleanupWorker>()
                .setInitialDelay(MmsDownloadCoordinator.DOWNLOAD_TTL_MS, TimeUnit.MILLISECONDS)
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
            if (!MmsDownloadCoordinator.isValidStagedFileName(fileName)) return
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(workName(fileName))
        }

        internal const val WORK_TAG = "sentinel-mms-download-cleanup"
        private const val WORK_PREFIX = "sentinel-mms-download-cleanup-v1-"
        private const val FILE_TAG_PREFIX = "sentinel-mms-download-file-"
        private const val KEY_FILE_NAME = "mms.download.file_name"

        private fun workName(fileName: String) = WORK_PREFIX + fileName.removeSuffix(".pdu")
        private fun fileTag(fileName: String) = FILE_TAG_PREFIX + fileName.removeSuffix(".pdu")
    }
}
