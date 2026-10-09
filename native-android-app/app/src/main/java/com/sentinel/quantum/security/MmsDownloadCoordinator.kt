package com.sentinel.quantum.security

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.util.UUID

/** Uses Android's public MMS transport API to retrieve a Notification.ind payload. */
object MmsDownloadCoordinator {
    sealed class Result {
        data class Requested(val subscriptionId: Int, val fileName: String) : Result()
        data class Rejected(val reason: String) : Result()
        data object NotNotification : Result()
    }

    fun request(context: Context, notificationPdu: ByteArray, sourceIntent: Intent): Result {
        val notification = MmsNotificationParser.parse(notificationPdu) ?: return Result.NotNotification
        if (!holdsSmsRole(context)) return Result.Rejected("SMS_ROLE_NOT_HELD")

        val subscriptionId = MmsSubscriptionResolver.resolve(context, sourceIntent)
        if (!MmsSubscriptionResolver.isValidSubscriptionId(subscriptionId)) {
            return Result.Rejected("MMS_SUBSCRIPTION_REQUIRED")
        }

        val canonicalCache = runCatching { context.cacheDir.canonicalFile }.getOrNull()
            ?: return Result.Rejected("MMS_CACHE_UNAVAILABLE")
        val directory = File(canonicalCache, DOWNLOAD_DIRECTORY)
        if (!directory.exists() && !directory.mkdirs()) {
            return Result.Rejected("MMS_DOWNLOAD_DIRECTORY_FAILED")
        }
        val canonicalDirectory = runCatching { directory.canonicalFile }.getOrNull()
            ?: return Result.Rejected("MMS_CACHE_UNAVAILABLE")
        if (canonicalDirectory.parentFile != canonicalCache) {
            return Result.Rejected("MMS_CACHE_PATH_REJECTED")
        }
        prune(context.applicationContext, canonicalDirectory)

        val token = UUID.randomUUID().toString()
        val fileName = "$token.pdu"
        val file = File(canonicalDirectory, fileName)
        val canonicalFile = runCatching { file.canonicalFile }.getOrNull()
            ?: return Result.Rejected("MMS_DOWNLOAD_TARGET_FAILED")
        if (canonicalFile.parentFile != canonicalDirectory) {
            return Result.Rejected("MMS_DOWNLOAD_TARGET_FAILED")
        }

        // Journal the routing identity before creating the staging file. If the process dies at any
        // later point, startup recovery can reconstruct both recovery and the original cleanup
        // deadline from this durable timestamp. A journal without a file is safely self-healing.
        val requestedAtMs = System.currentTimeMillis()
        val recoveryJournal = MmsDownloadRecoveryJournal(context.applicationContext)
        if (!recoveryJournal.record(canonicalFile.name, subscriptionId, requestedAtMs)) {
            return Result.Rejected("MMS_DOWNLOAD_RECOVERY_JOURNAL_FAILED")
        }
        if (!runCatching { canonicalFile.createNewFile() }.getOrDefault(false)) {
            recoveryJournal.remove(canonicalFile.name)
            return Result.Rejected("MMS_DOWNLOAD_TARGET_FAILED")
        }

        val contentUri = runCatching {
            FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                canonicalFile
            )
        }.getOrElse {
            delete(context, canonicalFile.name)
            return Result.Rejected("MMS_DOWNLOAD_URI_FAILED")
        }

        val safetyNetScheduled = runCatching {
            MmsDownloadCleanupWorker.scheduleAtDeadline(
                context = context.applicationContext,
                fileName = canonicalFile.name,
                requestedAtMs = requestedAtMs
            )
            MmsDownloadRecoveryWorker.schedule(context.applicationContext, canonicalFile.name)
            true
        }.getOrDefault(false)
        if (!safetyNetScheduled) {
            delete(context, canonicalFile.name)
            return Result.Rejected("MMS_DOWNLOAD_RECOVERY_SCHEDULE_FAILED")
        }

        return try {
            val callbackIntent = Intent(context, SentinelMmsDownloadReceiver::class.java)
                .setAction(ACTION_DOWNLOAD_COMPLETE)
                .setData(Uri.parse("sentinel-mms-download://result/$token"))
                .putExtra(EXTRA_FILE_NAME, canonicalFile.name)
                .putExtra(EXTRA_SUBSCRIPTION_ID, subscriptionId)
            val callback = PendingIntent.getBroadcast(
                context,
                token.hashCode(),
                callbackIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            @Suppress("DEPRECATION")
            SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
                .downloadMultimediaMessage(
                    context,
                    notification.contentLocation,
                    contentUri,
                    null,
                    callback
                )
            Result.Requested(subscriptionId, canonicalFile.name)
        } catch (_: Exception) {
            // The platform may throw after accepting the request, leaving a callback or a partial
            // payload in flight. Retain the staged target and both durable safety nets; deleting it
            // here can turn an ambiguous transport outcome into permanent MMS loss.
            LocalLogger(context.applicationContext).log(
                LocalLogger.LogLevel.WARNING,
                "MmsDownload",
                "Requête de téléchargement MMS ambiguë; copie temporaire conservée pour reprise"
            )
            Result.Rejected("MMS_DOWNLOAD_REQUEST_FAILED")
        }
    }

    fun delete(context: Context, fileName: String): Boolean =
        deleteInternal(
            context = context,
            fileName = fileName,
            cancelCleanup = true,
            cancelRecovery = true,
            removeRecoveryMetadata = true
        )

    /** Called only by the durable cleanup worker; never cancels that running worker itself. */
    internal fun expire(context: Context, fileName: String): Boolean =
        deleteInternal(
            context = context,
            fileName = fileName,
            cancelCleanup = false,
            cancelRecovery = true,
            removeRecoveryMetadata = true
        )

    /** Called after durable private persistence/provider handling succeeded. */
    internal fun finishRecovery(
        context: Context,
        fileName: String,
        cancelCleanupWorker: Boolean = true
    ): Boolean =
        deleteInternal(
            context = context,
            fileName = fileName,
            cancelCleanup = cancelCleanupWorker,
            cancelRecovery = false,
            removeRecoveryMetadata = true
        )

    private fun deleteInternal(
        context: Context,
        fileName: String,
        cancelCleanup: Boolean,
        cancelRecovery: Boolean,
        removeRecoveryMetadata: Boolean
    ): Boolean {
        if (!isValidStagedFileName(fileName)) return false
        val canonicalCache = runCatching { context.cacheDir.canonicalFile }.getOrNull()
            ?: return false
        val canonicalDirectory = runCatching { File(canonicalCache, DOWNLOAD_DIRECTORY).canonicalFile }
            .getOrNull() ?: return false
        if (canonicalDirectory.parentFile != canonicalCache) return false
        val file = runCatching { File(canonicalDirectory, fileName).canonicalFile }.getOrNull()
            ?: return false
        if (file.parentFile != canonicalDirectory) return false
        val removed = !file.exists() || runCatching { file.delete() }.getOrDefault(false)
        if (!removed) return false

        // Do not cancel the durable safety nets until the recovery journal is durably retired.
        // Otherwise a SharedPreferences commit failure can strand phantom recovery metadata with no
        // worker left to revisit it. A failed metadata retirement therefore stays fail-closed and
        // leaves the existing workers scheduled for a later cleanup/recovery pass.
        if (removeRecoveryMetadata) {
            val metadataRemoved = MmsDownloadRecoveryJournal(context.applicationContext).remove(fileName)
            if (!metadataRemoved) return false
        }
        if (cancelCleanup) {
            runCatching { MmsDownloadCleanupWorker.cancel(context.applicationContext, fileName) }
        }
        if (cancelRecovery) {
            runCatching { MmsDownloadRecoveryWorker.cancel(context.applicationContext, fileName) }
        }
        return true
    }

    /**
     * Secondary final-deadline recovery for stale, oversized or malformed staging files.
     *
     * Valid staged MMS files are never unlinked raw: if durable routing metadata exists, they get
     * the same final private/provider recovery attempt as the cleanup worker before staging is
     * retired. This also removes the matching recovery journal and worker state instead of leaving
     * phantom work behind. Invalid filenames cannot have valid journal entries and are deleted raw.
     */
    fun pruneExpired(context: Context): Int {
        val canonicalCache = runCatching { context.cacheDir.canonicalFile }.getOrNull() ?: return 0
        val directory = File(canonicalCache, DOWNLOAD_DIRECTORY)
        if (!directory.exists() || !directory.isDirectory) return 0
        val canonicalDirectory = runCatching { directory.canonicalFile }.getOrNull() ?: return 0
        if (canonicalDirectory.parentFile != canonicalCache) return 0
        return prune(context.applicationContext, canonicalDirectory)
    }

    private fun prune(context: Context, directory: File): Int {
        val cutoff = System.currentTimeMillis() - DOWNLOAD_TTL_MS
        var retired = 0
        directory.listFiles().orEmpty()
            .filter {
                it.isFile && (
                    it.lastModified() < cutoff ||
                    it.length() > MAX_DOWNLOADED_PDU_BYTES ||
                    !isValidStagedFileName(it.name)
                )
            }
            .forEach { file ->
                val existedBefore = file.exists()
                if (isValidStagedFileName(file.name)) {
                    if (MmsDownloadRecoveryJournal(context).read(file.name) != null) {
                        runCatching {
                            MmsDownloadRecovery.recover(
                                context = context,
                                fileName = file.name,
                                allowQuarantine = true
                            )
                        }
                    }
                    // Mirror the cleanup deadline even if final recovery could not complete: retire
                    // the staged file, its recovery metadata and any pending recovery worker.
                    expire(context, file.name)
                } else {
                    runCatching { file.delete() }
                }
                if (existedBefore && !file.exists()) retired++
            }
        return retired
    }

    private fun holdsSmsRole(context: Context): Boolean =
        context.readSmsRoleStateFailClosed() == SmsActivationDiagnostics.SmsRoleState.HELD

    internal fun isValidStagedFileName(fileName: String): Boolean = FILE_NAME.matches(fileName)

    /** Reads a completed transport file without trusting a concurrent size change. */
    internal fun readBounded(file: File, expectedSize: Long): ByteArray? {
        if (expectedSize !in 1L..MAX_DOWNLOADED_PDU_BYTES || expectedSize > Int.MAX_VALUE) {
            return null
        }
        return try {
            val bytes = ByteArray(expectedSize.toInt())
            FileInputStream(file).use { input ->
                var offset = 0
                while (offset < bytes.size) {
                    val read = input.read(bytes, offset, bytes.size - offset)
                    if (read <= 0) return null
                    offset += read
                }
                if (input.read() != -1) return null
            }
            bytes.takeIf { file.length() == expectedSize }
        } catch (_: Exception) {
            null
        }
    }

    const val ACTION_DOWNLOAD_COMPLETE = "com.sentinel.quantum.MMS_DOWNLOAD_COMPLETE"
    const val EXTRA_FILE_NAME = "mms.download.file"
    const val EXTRA_SUBSCRIPTION_ID = "mms.download.subscription"

    const val MAX_DOWNLOADED_PDU_BYTES = 17L * 1024L * 1024L
    const val DOWNLOAD_TTL_MS = 24L * 60L * 60L * 1000L

    internal const val DOWNLOAD_DIRECTORY = "sentinel_mms_download"
    private val FILE_NAME = Regex(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.pdu$"
    )
}
