package com.sentinel.quantum.security

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/** Handles the explicit callback from Android's MMS download transport. */
class SentinelMmsDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != MmsDownloadCoordinator.ACTION_DOWNLOAD_COMPLETE) return

        val fileName = intent.getStringExtra(MmsDownloadCoordinator.EXTRA_FILE_NAME)
            ?.takeIf { FILE_NAME.matches(it) }
            ?: return
        val directory = File(context.cacheDir, DOWNLOAD_DIRECTORY)
        val canonicalCache = runCatching { context.cacheDir.canonicalFile }.getOrNull() ?: return
        val canonicalDirectory = runCatching { directory.canonicalFile }.getOrNull() ?: return
        if (canonicalDirectory.parentFile != canonicalCache) return

        val target = runCatching { File(canonicalDirectory, fileName).canonicalFile }.getOrNull() ?: return
        if (target.parentFile != canonicalDirectory || !target.isFile) return

        if (resultCode != Activity.RESULT_OK) {
            runCatching { target.delete() }
            LocalLogger(context).log(
                LocalLogger.LogLevel.WARNING,
                "DefaultSms",
                "Téléchargement MMS Android échoué; code=$resultCode"
            )
            SmsNotificationHelper.notifyMessage(
                context,
                title = "MMS non téléchargé",
                preview = "Android n’a pas pu récupérer le MMS sur le réseau opérateur.",
                notificationId = fileName.hashCode()
            )
            return
        }

        val size = target.length()
        if (size !in 1..MmsDownloadCoordinator.MAX_DOWNLOADED_PDU_BYTES) {
            runCatching { target.delete() }
            LocalLogger(context).log(
                LocalLogger.LogLevel.SECURITY,
                "DefaultSms",
                "MMS téléchargé rejeté pour taille hors limite"
            )
            return
        }

        val data = runCatching { target.readBytes() }.getOrNull()
        runCatching { target.delete() }
        if (data == null || data.isEmpty()) return

        val safePreview = MmsDecodePipeline.decodeAndValidate(data, SentinelMmsPduDecoder)
        if (!persistPrivatePdu(context, data, safePreview)) return

        PhonePrivateTimelineStore(context).append(
            PhonePrivateTimeline.Event(
                kind = PhonePrivateTimeline.Kind.MMS,
                timestampMs = System.currentTimeMillis(),
                direction = "INCOMING",
                signal = if (safePreview is MmsDecodePipeline.Result.Accepted) {
                    "MMS_DOWNLOAD_SAFE_PREVIEW_READY"
                } else {
                    "MMS_DOWNLOAD_QUARANTINED"
                }
            )
        )

        SmsNotificationHelper.notifyMessage(
            context,
            title = "MMS reçu",
            preview = when (safePreview) {
                is MmsDecodePipeline.Result.Accepted ->
                    "Téléchargé par Android · ${safePreview.parts.size} partie(s) validée(s) pour aperçu sécurisé."
                is MmsDecodePipeline.Result.Rejected ->
                    "Téléchargé puis conservé en quarantaine locale."
            },
            notificationId = fileName.hashCode()
        )
    }

    private fun persistPrivatePdu(
        context: Context,
        data: ByteArray,
        safePreview: MmsDecodePipeline.Result
    ): Boolean {
        val directory = File(context.filesDir, "mms-inbox")
        if (!directory.exists() && !directory.mkdirs()) return false
        val canonicalRoot = runCatching { context.filesDir.canonicalFile }.getOrNull() ?: return false
        val canonicalDirectory = runCatching { directory.canonicalFile }.getOrNull() ?: return false
        if (canonicalDirectory.parentFile != canonicalRoot || !canonicalDirectory.isDirectory) return false

        val files = canonicalDirectory.listFiles()
            ?.filter { it.isFile && it.extension == "pdu" }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
        files.drop(MAX_STORED_MMS - 1).forEach { runCatching { it.delete() } }

        val digest = MessageDigest.getInstance("SHA-256")
            .digest(data)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            .take(24)
        val target = File(canonicalDirectory, "${System.currentTimeMillis()}-$digest.pdu")
        val canonicalTarget = runCatching { target.canonicalFile }.getOrNull() ?: return false
        if (canonicalTarget.parentFile != canonicalDirectory || canonicalTarget.exists()) return false

        return runCatching {
            FileOutputStream(canonicalTarget).use { stream ->
                stream.write(data)
                stream.fd.sync()
            }
            LocalLogger(context).log(
                LocalLogger.LogLevel.SECURITY,
                "DefaultSms",
                "MMS téléchargé conservé localement; taille=${data.size}; preview=" +
                    if (safePreview is MmsDecodePipeline.Result.Accepted) "SAFE" else "QUARANTINED"
            )
            true
        }.getOrElse {
            runCatching { canonicalTarget.delete() }
            false
        }
    }

    private companion object {
        val FILE_NAME = Regex("^[0-9a-fA-F-]{36}\\.pdu$")
        const val DOWNLOAD_DIRECTORY = "sentinel_mms_download"
        const val MAX_STORED_MMS = 50
    }
}
