package com.sentinel.quantum.security

import android.app.PendingIntent
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import androidx.core.content.FileProvider
import java.io.File
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

        val subscriptionId = resolveSubscriptionId(sourceIntent)
        if (subscriptionId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            return Result.Rejected("MMS_SUBSCRIPTION_REQUIRED")
        }

        val directory = File(context.cacheDir, DOWNLOAD_DIRECTORY)
        if (!directory.exists() && !directory.mkdirs()) {
            return Result.Rejected("MMS_DOWNLOAD_DIRECTORY_FAILED")
        }
        val canonicalCache = runCatching { context.cacheDir.canonicalFile }.getOrNull()
            ?: return Result.Rejected("MMS_CACHE_UNAVAILABLE")
        val canonicalDirectory = runCatching { directory.canonicalFile }.getOrNull()
            ?: return Result.Rejected("MMS_CACHE_UNAVAILABLE")
        if (canonicalDirectory.parentFile != canonicalCache) {
            return Result.Rejected("MMS_CACHE_PATH_REJECTED")
        }
        prune(canonicalDirectory)

        val token = UUID.randomUUID().toString()
        val file = File(canonicalDirectory, "$token.pdu")
        val canonicalFile = runCatching { file.canonicalFile }.getOrNull()
            ?: return Result.Rejected("MMS_DOWNLOAD_TARGET_FAILED")
        if (canonicalFile.parentFile != canonicalDirectory || !runCatching { canonicalFile.createNewFile() }.getOrDefault(false)) {
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

        return try {
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
            canonicalFile.delete()
            Result.Rejected("MMS_DOWNLOAD_REQUEST_FAILED")
        }
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

    private fun prune(directory: File) {
        val cutoff = System.currentTimeMillis() - DOWNLOAD_TTL_MS
        directory.listFiles().orEmpty()
            .filter { it.isFile && (it.lastModified() < cutoff || it.length() > MAX_DOWNLOADED_PDU_BYTES) }
            .forEach { runCatching { it.delete() } }
    }

    private fun holdsSmsRole(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val manager = context.getSystemService(RoleManager::class.java)
            manager.isRoleAvailable(RoleManager.ROLE_SMS) && manager.isRoleHeld(RoleManager.ROLE_SMS)
        } else {
            Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
        }

    const val ACTION_DOWNLOAD_COMPLETE = "com.sentinel.quantum.MMS_DOWNLOAD_COMPLETE"
    const val EXTRA_FILE_NAME = "mms.download.file"
    const val EXTRA_SUBSCRIPTION_ID = "mms.download.subscription"

    const val MAX_DOWNLOADED_PDU_BYTES = 17L * 1024L * 1024L

    private const val DOWNLOAD_DIRECTORY = "sentinel_mms_download"
    private const val DOWNLOAD_TTL_MS = 24L * 60L * 60L * 1000L
}
