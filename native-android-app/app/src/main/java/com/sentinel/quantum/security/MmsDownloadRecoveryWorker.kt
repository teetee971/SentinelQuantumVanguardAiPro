package com.sentinel.quantum.security

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Re-ingests a staged incoming MMS when Android filled the destination file but the callback was
 * lost (for example because the process died). This is deliberately separate from the 24 h cleanup
 * worker: cleanup bounds retention, while this worker restores product state.
 */
class MmsDownloadRecoveryWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {
    override fun doWork(): Result {
        val fileName = inputData.getString(KEY_FILE_NAME)?.takeIf { it.isNotBlank() }
        if (fileName == null) {
            val recoveryScheduled = runCatching {
                MmsDownloadRecoveryWorker.schedulePendingNow(applicationContext)
                true
            }.getOrDefault(false)
            LocalLogger(applicationContext).log(
                LocalLogger.LogLevel.WARNING,
                "MmsDownload",
                "Identifiant de reprise MMS manquant; demande non acquittée"
            )
            return if (recoveryScheduled) Result.failure() else Result.retry()
        }
        val outcome = runCatching {
            MmsDownloadRecovery.recover(
                context = applicationContext,
                fileName = fileName,
                allowQuarantine = false
            )
        }.getOrElse {
            LocalLogger(applicationContext).log(
                LocalLogger.LogLevel.WARNING,
                "MmsDownload",
                "Échec inattendu de la reprise MMS; nouvelle tentative durable planifiée"
            )
            return Result.retry()
        }
        return when (outcome) {
            MmsDownloadRecovery.Outcome.RETRY -> Result.retry()
            MmsDownloadRecovery.Outcome.RECOVERED,
            MmsDownloadRecovery.Outcome.TERMINAL -> Result.success()
        }
    }

    companion object {
        fun schedule(context: Context, fileName: String) {
            enqueue(context, fileName, RECOVERY_DELAY_MS, ExistingWorkPolicy.KEEP)
        }

        /** Wakes an already journaled callback immediately after the receiver pool saturates. */
        fun scheduleNow(context: Context, fileName: String) {
            enqueue(context, fileName, 0L, ExistingWorkPolicy.REPLACE)
        }

        fun schedulePendingNow(context: Context) {
            MmsDownloadRecoveryJournal(context).all().forEach { record ->
                MmsDownloadCleanupWorker.scheduleAtDeadline(
                    context = context,
                    fileName = record.fileName,
                    requestedAtMs = record.requestedAtMs
                )
                enqueue(context, record.fileName, 0L, ExistingWorkPolicy.REPLACE)
            }
        }

        fun cancel(context: Context, fileName: String) {
            if (!MmsDownloadCoordinator.isValidStagedFileName(fileName)) return
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(workName(fileName))
        }

        private fun enqueue(
            context: Context,
            fileName: String,
            delayMs: Long,
            policy: ExistingWorkPolicy
        ) {
            require(MmsDownloadCoordinator.isValidStagedFileName(fileName)) {
                "invalid MMS download file"
            }
            val builder = OneTimeWorkRequestBuilder<MmsDownloadRecoveryWorker>()
                .setInputData(workDataOf(KEY_FILE_NAME to fileName))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.SECONDS)
                .addTag(WORK_TAG)
                .addTag(fileTag(fileName))
            if (delayMs > 0L) builder.setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                workName(fileName),
                policy,
                builder.build()
            )
        }

        internal const val RECOVERY_DELAY_MS = 2L * 60L * 1000L
        internal const val WORK_TAG = "sentinel-mms-download-recovery"
        private const val WORK_PREFIX = "sentinel-mms-download-recovery-v1-"
        private const val FILE_TAG_PREFIX = "sentinel-mms-download-recovery-file-"
        private const val KEY_FILE_NAME = "mms.download.recovery.file_name"

        private fun workName(fileName: String) = WORK_PREFIX + fileName.removeSuffix(".pdu")
        private fun fileTag(fileName: String) = FILE_TAG_PREFIX + fileName.removeSuffix(".pdu")
    }
}

/** Shared recovery engine used by delayed WorkManager recovery and the final cleanup deadline. */
internal object MmsDownloadRecovery {
    enum class Outcome {
        RECOVERED,
        RETRY,
        TERMINAL
    }

    fun recover(
        context: Context,
        fileName: String,
        allowQuarantine: Boolean,
        nowMs: Long = System.currentTimeMillis()
    ): Outcome {
        val appContext = context.applicationContext
        val journal = MmsDownloadRecoveryJournal(appContext)
        val record = journal.read(fileName)
        if (record == null) {
            val staged = stagedFile(appContext, fileName)
            return if (staged != null || journal.hasRecord(fileName)) {
                Outcome.RETRY
            } else {
                Outcome.TERMINAL
            }
        }
        if (nowMs < 0L || record.requestedAtMs > nowMs) return Outcome.RETRY

        if (
            appContext.readSmsRoleStateFailClosed() !=
                SmsActivationDiagnostics.SmsRoleState.HELD
        ) {
            if (!finish(appContext, fileName, allowQuarantine)) return Outcome.RETRY
            return Outcome.TERMINAL
        }
        if (!MmsSubscriptionResolver.isValidSubscriptionId(record.subscriptionId)) {
            if (!finish(appContext, fileName, allowQuarantine)) return Outcome.RETRY
            return Outcome.TERMINAL
        }

        val target = stagedFile(appContext, fileName) ?: run {
            if (!journal.remove(fileName)) return Outcome.RETRY
            return Outcome.TERMINAL
        }
        val sizeBefore = target.length()
        if (sizeBefore <= 0L) return Outcome.RETRY
        if (sizeBefore > MmsDownloadCoordinator.MAX_DOWNLOADED_PDU_BYTES) {
            if (!finish(appContext, fileName, allowQuarantine)) return Outcome.RETRY
            return Outcome.TERMINAL
        }

        val modifiedBefore = target.lastModified()
        if (modifiedBefore <= 0L || nowMs - modifiedBefore < STABLE_FILE_GRACE_MS) {
            return Outcome.RETRY
        }
        val data = runCatching {
            MmsDownloadCoordinator.readBounded(target, sizeBefore)
        }.getOrNull() ?: return Outcome.RETRY
        if (
            data.isEmpty() ||
            data.size.toLong() != sizeBefore ||
            target.length() != sizeBefore ||
            target.lastModified() != modifiedBefore
        ) {
            return Outcome.RETRY
        }

        val digest = IncomingMmsIdentity.sha256Hex(data) ?: return Outcome.RETRY
        val prepared = IncomingMmsProjectionPipeline.prepare(data, digest, record.subscriptionId)
        if (shouldRetryQuarantine(prepared, allowQuarantine)) {
            return Outcome.RETRY
        }

        val persistence = IncomingMmsPrivateStore.persist(appContext.filesDir, data)
        val persistedDigest = persistence.digestHex
        if (persistence.state == IncomingMmsPrivateStore.State.FAILED || persistedDigest == null) {
            return Outcome.RETRY
        }
        if (persistedDigest != digest) return Outcome.RETRY

        val providerResult = when (prepared) {
            is IncomingMmsProjectionPipeline.Result.Ready -> {
                val store = IncomingMmsConversationStore(appContext)
                runCatching { store.repairJournal() }
                store.project(prepared.plan)
            }
            is IncomingMmsProjectionPipeline.Result.Quarantined -> null
        }

        val providerInserted =
            providerResult is IncomingMmsConversationStore.ProjectResult.Ready && !providerResult.replay
        val providerReplay =
            providerResult is IncomingMmsConversationStore.ProjectResult.Ready && providerResult.replay
        val providerRejected =
            providerResult is IncomingMmsConversationStore.ProjectResult.Rejected

        // A private PDU is durable, but it is not equivalent to a message visible in the canonical
        // Android conversation. Keep staged bytes + recovery metadata active on provider failure so
        // transient provider/process conditions can be retried. At the bounded cleanup deadline,
        // however, private durability is accepted explicitly and staging is retired as quarantine.
        if (shouldRetryProviderProjection(providerResult, allowQuarantine)) {
            if (persistence.state == IncomingMmsPrivateStore.State.CREATED) {
                runCatching {
                    PhonePrivateTimelineStore(appContext).append(
                        PhonePrivateTimeline.Event(
                            kind = PhonePrivateTimeline.Kind.MMS,
                            timestampMs = nowMs,
                            direction = "INCOMING",
                            signal = "MMS_DOWNLOAD_RECOVERED_PROVIDER_RETRY_PENDING"
                        )
                    )
                }
            }
            return Outcome.RETRY
        }

        if (
            persistence.state == IncomingMmsPrivateStore.State.CREATED ||
            providerInserted ||
            (providerRejected && allowQuarantine)
        ) {
            runCatching {
                PhonePrivateTimelineStore(appContext).append(
                    PhonePrivateTimeline.Event(
                        kind = PhonePrivateTimeline.Kind.MMS,
                        timestampMs = nowMs,
                        direction = "INCOMING",
                        signal = when {
                            providerInserted -> "MMS_DOWNLOAD_RECOVERED_PROVIDER_READY"
                            providerReplay -> "MMS_DOWNLOAD_RECOVERED_PROVIDER_REPLAY"
                            providerRejected -> "MMS_DOWNLOAD_RECOVERED_PRIVATE_FINAL"
                            else -> "MMS_DOWNLOAD_RECOVERED_PRIVATE_ONLY"
                        }
                    )
                )
            }
        }

        if (
            persistence.state == IncomingMmsPrivateStore.State.CREATED ||
            providerInserted ||
            (providerRejected && allowQuarantine)
        ) {
            SmsNotificationHelper.notifyMessage(
                appContext,
                title = "MMS récupéré",
                preview = when {
                    providerInserted -> "Le MMS a été restauré dans la conversation Android après reprise."
                    providerReplay -> "Le MMS avait déjà été restauré; aucun doublon n’a été ajouté."
                    providerRejected -> "Le MMS a été conservé localement; la projection Android n’a pas abouti avant le délai final."
                    else -> "Le MMS a été conservé en quarantaine locale après reprise."
                },
                notificationId = digest.take(16).hashCode()
            )
        }

        if (!finish(appContext, fileName, allowQuarantine)) return Outcome.RETRY
        return Outcome.RECOVERED
    }

    /**
     * PLAN:* means the envelope, multipart body and safety boundary all succeeded and only the
     * canonical-provider plan refused projection. The staged file is therefore complete enough for
     * immediate private quarantine. Earlier ENVELOPE/BODY/SAFETY failures can still represent a
     * partially written lost-callback file, so they retry until the bounded final deadline.
     */
    internal fun shouldRetryQuarantine(
        prepared: IncomingMmsProjectionPipeline.Result,
        allowQuarantine: Boolean
    ): Boolean =
        prepared is IncomingMmsProjectionPipeline.Result.Quarantined &&
            !allowQuarantine &&
            !prepared.reason.startsWith("PLAN:")

    internal fun shouldRetryProviderProjection(
        providerResult: IncomingMmsConversationStore.ProjectResult?,
        allowQuarantine: Boolean
    ): Boolean =
        providerResult is IncomingMmsConversationStore.ProjectResult.Rejected && !allowQuarantine

    private fun finish(context: Context, fileName: String, fromCleanupDeadline: Boolean): Boolean =
        MmsDownloadCoordinator.finishRecovery(
            context = context,
            fileName = fileName,
            cancelCleanupWorker = !fromCleanupDeadline
        )

    private fun stagedFile(context: Context, fileName: String): File? {
        if (!MmsDownloadCoordinator.isValidStagedFileName(fileName)) return null
        val canonicalCache = runCatching { context.cacheDir.canonicalFile }.getOrNull() ?: return null
        val directory = File(canonicalCache, MmsDownloadCoordinator.DOWNLOAD_DIRECTORY)
        val canonicalDirectory = runCatching { directory.canonicalFile }.getOrNull() ?: return null
        if (canonicalDirectory.parentFile != canonicalCache) return null
        val target = runCatching { File(canonicalDirectory, fileName).canonicalFile }.getOrNull()
            ?: return null
        if (target.parentFile != canonicalDirectory || !target.isFile) return null
        return target
    }

    internal const val STABLE_FILE_GRACE_MS = 30_000L
}
