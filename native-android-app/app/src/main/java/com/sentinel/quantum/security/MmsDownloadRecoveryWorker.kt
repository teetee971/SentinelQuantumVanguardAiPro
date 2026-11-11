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
        val fileName = inputData.getString(KEY_FILE_NAME) ?: return Result.success()
        return when (
            MmsDownloadRecovery.recover(
                context = applicationContext,
                fileName = fileName,
                allowQuarantine = false
            )
        ) {
            MmsDownloadRecovery.Outcome.RETRY -> Result.retry()
            MmsDownloadRecovery.Outcome.RECOVERED,
            MmsDownloadRecovery.Outcome.TERMINAL,
            MmsDownloadRecovery.Outcome.DIAGNOSE -> Result.success()
        }
    }

    companion object {
        fun schedule(context: Context, fileName: String) {
            enqueue(context, fileName, RECOVERY_DELAY_MS, ExistingWorkPolicy.KEEP)
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
        TERMINAL,
        /** Stop scheduling, but keep the journal and the staging bytes for diagnosis. */
        DIAGNOSE
    }

    fun recover(
        context: Context,
        fileName: String,
        allowQuarantine: Boolean,
        nowMs: Long = System.currentTimeMillis()
    ): Outcome = exclusively(fileName) {
        recoverExclusive(context, fileName, allowQuarantine, nowMs)
    }

    /** Serializes recovery, cleanup and the download callback for one staging name. */
    inline fun <T> exclusively(fileName: String, block: () -> T): T =
        MmsDownloadProcessingLock.withFile(fileName, block)

    /**
     * A retry or a diagnosis still needs the bytes. Cleanup must not unlink them.
     * Terminal and recovered passes have already kept a private copy or proved there is nothing to keep.
     */
    fun cleanupMustKeepStaging(outcome: Outcome): Boolean =
        outcome == Outcome.RETRY || outcome == Outcome.DIAGNOSE

    private fun recoverExclusive(
        context: Context,
        fileName: String,
        allowQuarantine: Boolean,
        nowMs: Long
    ): Outcome {
        val appContext = context.applicationContext
        val journal = MmsDownloadRecoveryJournal(appContext)
        val record = journal.read(fileName) ?: return Outcome.TERMINAL
        if (nowMs < 0L || record.requestedAtMs > nowMs) return Outcome.RETRY

        val target = stagedFile(appContext, fileName)
        if (target == null) {
            return recoverWithoutStaging(appContext, journal, record, allowQuarantine, nowMs)
        }
        val sizeBefore = target.length()
        if (sizeBefore <= 0L) {
            return if (allowQuarantine) {
                finishTerminal(appContext, fileName, fromCleanupDeadline = true)
            } else {
                Outcome.RETRY
            }
        }
        if (sizeBefore > MmsDownloadCoordinator.MAX_DOWNLOADED_PDU_BYTES) {
            return finishTerminal(appContext, fileName, allowQuarantine)
        }

        val modifiedBefore = target.lastModified()
        if (modifiedBefore <= 0L || nowMs - modifiedBefore < STABLE_FILE_GRACE_MS) {
            return Outcome.RETRY
        }
        val data = runCatching { target.readBytes() }.getOrNull() ?: return Outcome.RETRY
        if (
            data.isEmpty() ||
            data.size.toLong() != sizeBefore ||
            target.length() != sizeBefore ||
            target.lastModified() != modifiedBefore
        ) {
            return Outcome.RETRY
        }

        val digest = IncomingMmsIdentity.sha256Hex(data) ?: return Outcome.RETRY
        val persistence = IncomingMmsPrivateStore.confirmDurableCopy(
            appContext.filesDir,
            data,
            journal.pinnedDigests()
        )
        if (persistence == null || persistence.digestHex != digest) return Outcome.RETRY
        if (
            !journal.markDigest(
                fileName = fileName,
                digestHex = digest,
                expectedSubscriptionId = record.subscriptionId,
                expectedRequestedAtMs = record.requestedAtMs
            )
        ) {
            return Outcome.RETRY
        }
        IncomingMmsPrivateStore.releaseJournaled(digest)
        if (!MmsDownloadDurability.stagingDeletable(privateRereadMatches = true, journalAccepted = true)) {
            return Outcome.RETRY
        }

        if (
            appContext.readSmsRoleStateFailClosed() !=
                SmsActivationDiagnostics.SmsRoleState.HELD
        ) {
            check(!MmsDownloadDurability.roleRevocationDeletesStaging())
            return Outcome.RETRY
        }
        if (!MmsSubscriptionResolver.isValidSubscriptionId(record.subscriptionId)) {
            return Outcome.DIAGNOSE
        }

        return projectAndFinish(
            appContext = appContext,
            journal = journal,
            fileName = fileName,
            subscriptionId = record.subscriptionId,
            data = data,
            digest = digest,
            persistence = persistence,
            allowQuarantine = allowQuarantine,
            nowMs = nowMs,
            stagingPresent = true
        )
    }

    private fun recoverWithoutStaging(
        appContext: Context,
        journal: MmsDownloadRecoveryJournal,
        record: MmsDownloadRecoveryJournal.Record,
        allowQuarantine: Boolean,
        nowMs: Long
    ): Outcome {
        val digest = record.digestHex
        val data = digest?.let { IncomingMmsPrivateStore.read(appContext.filesDir, it) }
        val privateReadable = digest != null &&
            data != null &&
            IncomingMmsIdentity.sha256Hex(data) == digest
        val disposition = MmsDownloadMissingCopyPolicy.disposition(
            digestPresent = digest != null,
            privateReadable = privateReadable,
            observations = record.privateMissCount
        )
        return when (disposition) {
            MmsDownloadMissingCopyPolicy.Disposition.CONTINUE_PRIVATE -> {
                if (
                    appContext.readSmsRoleStateFailClosed() !=
                        SmsActivationDiagnostics.SmsRoleState.HELD ||
                    !MmsSubscriptionResolver.isValidSubscriptionId(record.subscriptionId)
                ) {
                    return Outcome.RETRY
                }
                projectAndFinish(
                    appContext = appContext,
                    journal = journal,
                    fileName = record.fileName,
                    subscriptionId = record.subscriptionId,
                    data = requireNotNull(data),
                    digest = requireNotNull(digest),
                    persistence = IncomingMmsPrivateStore.Result(
                        IncomingMmsPrivateStore.State.EXISTING,
                        digest
                    ),
                    allowQuarantine = allowQuarantine,
                    nowMs = nowMs,
                    stagingPresent = false
                )
            }
            MmsDownloadMissingCopyPolicy.Disposition.RETRY_INCONSISTENT,
            MmsDownloadMissingCopyPolicy.Disposition.RETRY_HISTORICAL -> {
                journal.notePrivateUnavailable(
                    fileName = record.fileName,
                    expectedSubscriptionId = record.subscriptionId,
                    expectedRequestedAtMs = record.requestedAtMs
                )
                Outcome.RETRY
            }
            MmsDownloadMissingCopyPolicy.Disposition.DIAGNOSE_INCONSISTENT,
            MmsDownloadMissingCopyPolicy.Disposition.DIAGNOSE_HISTORICAL -> {
                LocalLogger(appContext).log(
                    LocalLogger.LogLevel.WARNING,
                    "MmsDownload",
                    "Reprise MMS arrêtée sans effacer le journal; disposition=$disposition"
                )
                SmsNotificationHelper.notifyMessage(
                    appContext,
                    title = "MMS à vérifier",
                    preview = "Une copie MMS n’a pas pu être relue. Le journal est conservé.",
                    notificationId = record.fileName.hashCode()
                )
                Outcome.DIAGNOSE
            }
        }
    }

    private fun projectAndFinish(
        appContext: Context,
        journal: MmsDownloadRecoveryJournal,
        fileName: String,
        subscriptionId: Int,
        data: ByteArray,
        digest: String,
        persistence: IncomingMmsPrivateStore.Result,
        allowQuarantine: Boolean,
        nowMs: Long,
        stagingPresent: Boolean
    ): Outcome {
        val prepared = IncomingMmsProjectionPipeline.prepare(data, digest, subscriptionId)
        if (shouldRetryQuarantine(prepared, allowQuarantine)) {
            return Outcome.RETRY
        }

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

        if (!stagingPresent) {
            return if (journal.remove(fileName)) Outcome.RECOVERED else Outcome.RETRY
        }
        return if (finish(appContext, fileName, allowQuarantine)) {
            Outcome.RECOVERED
        } else {
            Outcome.RETRY
        }
    }

    private fun finishTerminal(
        context: Context,
        fileName: String,
        fromCleanupDeadline: Boolean
    ): Outcome = if (finish(context, fileName, fromCleanupDeadline)) {
        Outcome.TERMINAL
    } else {
        Outcome.RETRY
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
