package com.sentinel.quantum.security

import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Bounded WAP/MMS intake for the staged default-SMS client.
 *
 * The raw PDU remains in app-private storage, is never uploaded, and is only accepted while
 * Sentinel is actually the user-selected default SMS handler. A bounded decoder is applied only
 * to derive a fail-closed safe-preview state; unsupported or malformed content remains quarantined.
 */
class SentinelMmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
        if (!holdsSmsRole(context)) return
        if (intent.type.orEmpty() != MMS_MIME_TYPE) return

        val data = intent.getByteArrayExtra("data") ?: return
        if (data.isEmpty() || data.size > MAX_PDU_BYTES) return
        val safePreview = MmsDecodePipeline.decodeAndValidate(data, SentinelMmsPduDecoder)

        val directory = File(context.filesDir, "mms-inbox")
        if (!directory.exists() && !directory.mkdirs()) return
        val canonicalRoot = runCatching { context.filesDir.canonicalFile }.getOrNull() ?: return
        val canonicalDirectory = runCatching { directory.canonicalFile }.getOrNull() ?: return
        if (canonicalDirectory.parentFile != canonicalRoot || !canonicalDirectory.isDirectory) return
        prune(canonicalDirectory)

        val digest = MessageDigest.getInstance("SHA-256")
            .digest(data)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            .take(24)

        val target = File(canonicalDirectory, "${System.currentTimeMillis()}-$digest.pdu")
        val canonicalTarget = runCatching { target.canonicalFile }.getOrNull() ?: return
        if (canonicalTarget.parentFile != canonicalDirectory || canonicalTarget.exists()) return
        runCatching {
            FileOutputStream(canonicalTarget).use { stream ->
                stream.write(data)
                stream.fd.sync()
            }
        }.onSuccess {
            PhonePrivateTimelineStore(context).append(
                PhonePrivateTimeline.Event(
                    kind = PhonePrivateTimeline.Kind.MMS,
                    timestampMs = System.currentTimeMillis(),
                    direction = "INCOMING",
                    signal = if (safePreview is MmsDecodePipeline.Result.Accepted) {
                        "MMS_SAFE_PREVIEW_READY"
                    } else {
                        "MMS_LOCAL_QUARANTINE"
                    }
                )
            )
            SmsNotificationHelper.notifyMessage(
                context,
                title = "MMS reçu",
                preview = when (safePreview) {
                    is MmsDecodePipeline.Result.Accepted ->
                        "MMS conservé localement · aperçu sécurisé: ${safePreview.parts.size} partie(s) validée(s)."
                    is MmsDecodePipeline.Result.Rejected ->
                        "MMS conservé en quarantaine locale · aperçu refusé: ${safePreview.reason.take(48)}."
                },
                notificationId = digest.hashCode()
            )
            LocalLogger(context).log(
                LocalLogger.LogLevel.SECURITY,
                "DefaultSms",
                "MMS entrant conservé localement; taille=${data.size}; preview=" +
                    if (safePreview is MmsDecodePipeline.Result.Accepted) "SAFE" else "QUARANTINED"
            )
        }.onFailure {
            runCatching { canonicalTarget.delete() }
            LocalLogger(context).log(
                LocalLogger.LogLevel.WARNING,
                "DefaultSms",
                "Échec de conservation locale d'un MMS"
            )
        }
    }

    private fun prune(directory: File) {
        val files = directory.listFiles()
            ?.filter { it.isFile && it.extension == "pdu" }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

        files.drop(MAX_STORED_MMS - 1).forEach { file ->
            runCatching { file.delete() }
        }
    }

    private fun holdsSmsRole(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val manager = context.getSystemService(RoleManager::class.java)
            manager.isRoleAvailable(RoleManager.ROLE_SMS) &&
                manager.isRoleHeld(RoleManager.ROLE_SMS)
        } else {
            Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
        }
    }

    companion object {
        private const val MMS_MIME_TYPE = "application/vnd.wap.mms-message"
        private const val MAX_PDU_BYTES = 512 * 1024
        private const val MAX_STORED_MMS = 50
    }
}
