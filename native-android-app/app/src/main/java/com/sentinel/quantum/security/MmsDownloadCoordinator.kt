package com.sentinel.quantum.security

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/** Uses Android's public MMS transport API to retrieve a validated Notification.ind payload. */
object MmsDownloadCoordinator {
    sealed class Result {
        data class Requested(val subscriptionId: Int, val fileName: String) : Result()
        data class Rejected(val reason: String) : Result()
        data object NotNotification : Result()
    }

    fun request(context: Context, notificationPdu: ByteArray, sourceIntent: Intent): Result {
        val notification = when (val inspection = MmsNotificationParser.inspect(notificationPdu)) {
            is MmsNotificationParser.Inspection.Accepted -> inspection.notification
            is MmsNotificationParser.Inspection.Rejected -> return Result.Rejected(inspection.reason)
            MmsNotificationParser.Inspection.NotNotification -> return Result.NotNotification
        }
        if (!holdsSmsRole(context)) return Result.Rejected("SMS_ROLE_NOT_HELD")

        val subscriptionId = resolveSubscriptionId(sourceIntent)
        if (subscriptionId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
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
        prune(canonicalDirectory)

        val token = UUID.randomUUID().toString()
        val fileName = "$token.pdu"
        val file = File(canonicalDirectory, fileName)
        val canonicalFile = runCatching { file.canonicalFile }.getOrNull()
            ?: return Result.Rejected("MMS_DOWNLOAD_TARGET_FAILED")
        if (
            canonicalFile.parentFile != canonicalDirectory ||
            !runCatching { canonicalFile.createNewFile() }.getOrDefault(false)
        ) {
            return Result.Rejected("MMS_DOWNLOAD_TARGET_FAILED")
        }

        val contentUri = runCatching {
            FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                canonicalFile
            )
        }.getOrElse {
            canonicalFile.delete()
            return Result.Rejected("MMS_DOWNLOAD_URI_FAILED")
        }

        val cleanupScheduled = runCatching {
            MmsDownloadCleanupWorker.schedule(context.applicationContext, canonicalFile.name)
            true
        }.getOrDefault(false)
        if (!cleanupScheduled) {
            canonicalFile.delete()
            return Result.Rejected("MMS_DOWNLOAD_CLEANUP_SCHEDULE_FAILED")
        }

        return try {
            val callbackIntent = Intent(context, SentinelMmsDownloadReceiver::class.java)
                .setAction(ACTION_DOWNLOAD_COMPLETE)
                .setData(Uri.parse("sentinel-mms-download://result/$token"))
                .putExtra(EXTRA_FILE_NAME, canonicalFile.name)
                .putExtra(EXTRA_SUBSCRIPTION_ID, subscriptionId)
                .putExtra(EXTRA_TRANSACTION_ID, notification.transactionId)
            when (val senderIdentity = notification.senderIdentity) {
                is MmsNotificationParser.SenderIdentity.Verified ->
                    callbackIntent.putExtra(EXTRA_VERIFIED_SENDER, senderIdentity.address)
                is MmsNotificationParser.SenderIdentity.Unavailable ->
                    callbackIntent.putExtra(EXTRA_SENDER_UNAVAILABLE_REASON, senderIdentity.reason)
            }
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
            delete(context, canonicalFile.name)
            Result.Rejected("MMS_DOWNLOAD_REQUEST_FAILED")
        }
    }

    fun delete(context: Context, fileName: String): Boolean =
        deleteInternal(context, fileName, cancelCleanup = true)

    /** Called only by the durable worker for this file; never cancels the running worker itself. */
    internal fun expire(context: Context, fileName: String): Boolean =
        deleteInternal(context, fileName, cancelCleanup = false)

    private fun deleteInternal(context: Context, fileName: String, cancelCleanup: Boolean): Boolean {
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
        if (removed && cancelCleanup) {
            runCatching { MmsDownloadCleanupWorker.cancel(context.applicationContext, fileName) }
        }
        return removed
    }

    /** Secondary recovery path for stale, oversized or malformed files. */
    fun pruneExpired(context: Context): Int {
        val canonicalCache = runCatching { context.cacheDir.canonicalFile }.getOrNull() ?: return 0
        val directory = File(canonicalCache, DOWNLOAD_DIRECTORY)
        if (!directory.exists() || !directory.isDirectory) return 0
        val canonicalDirectory = runCatching { directory.canonicalFile }.getOrNull() ?: return 0
        if (canonicalDirectory.parentFile != canonicalCache) return 0
        return prune(canonicalDirectory)
    }

    private fun resolveSubscriptionId(intent: Intent): Int {
        val fromPlatform = intent.getIntExtra(
            SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,
            SubscriptionManager.INVALID_SUBSCRIPTION_ID
        )
        if (fromPlatform != SubscriptionManager.INVALID_SUBSCRIPTION_ID) return fromPlatform

        // Older telephony stacks used this extra name for WAP push delivery.
        val legacy = intent.getIntExtra("subscription", SubscriptionManager.INVALID_SUBSCRIPTION_ID)
        if (legacy != SubscriptionManager.INVALID_SUBSCRIPTION_ID) return legacy

        return SubscriptionManager.getDefaultSmsSubscriptionId()
    }

    private fun prune(directory: File): Int {
        val cutoff = System.currentTimeMillis() - DOWNLOAD_TTL_MS
        var deleted = 0
        directory.listFiles().orEmpty()
            .filter {
                it.isFile && (
                    it.lastModified() < cutoff ||
                    it.length() > MAX_DOWNLOADED_PDU_BYTES ||
                    !isValidStagedFileName(it.name)
                )
            }
            .forEach { if (runCatching { it.delete() }.getOrDefault(false)) deleted++ }
        return deleted
    }

    private fun holdsSmsRole(context: Context): Boolean =
        context.readSmsRoleStateFailClosed() == SmsActivationDiagnostics.SmsRoleState.HELD

    internal fun isValidStagedFileName(fileName: String): Boolean = FILE_NAME.matches(fileName)

    const val ACTION_DOWNLOAD_COMPLETE = "com.sentinel.quantum.MMS_DOWNLOAD_COMPLETE"
    const val EXTRA_FILE_NAME = "mms.download.file"
    const val EXTRA_SUBSCRIPTION_ID = "mms.download.subscription"
    const val EXTRA_TRANSACTION_ID = "mms.download.transaction_id"
    const val EXTRA_VERIFIED_SENDER = "mms.download.verified_sender"
    const val EXTRA_SENDER_UNAVAILABLE_REASON = "mms.download.sender_unavailable_reason"

    const val MAX_DOWNLOADED_PDU_BYTES = 17L * 1024L * 1024L
    const val DOWNLOAD_TTL_MS = 24L * 60L * 60L * 1000L

    internal const val DOWNLOAD_DIRECTORY = "sentinel_mms_download"
    private val FILE_NAME = Regex(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.pdu$"
    )
}
