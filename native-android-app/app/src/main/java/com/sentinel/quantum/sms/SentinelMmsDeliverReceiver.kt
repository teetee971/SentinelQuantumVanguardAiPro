package com.sentinel.quantum.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import java.io.File
import java.security.MessageDigest

/**
 * Bounded raw WAP/MMS intake used while the full MMS presentation pipeline is validated.
 * The payload stays in app-private storage and is never uploaded.
 */
class SentinelMmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
        if (!SmsProviderStore.isDefaultSmsHandler(context)) return
        val data = intent.getByteArrayExtra("data") ?: return
        if (data.isEmpty() || data.size > MAX_PDU_BYTES) return
        val directory = File(context.filesDir, "mms-inbox").apply { mkdirs() }
        prune(directory)
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            .take(24)
        val file = File(directory, "${System.currentTimeMillis()}-$digest.pdu")
        runCatching { file.writeBytes(data) }.onSuccess {
            SmsNotificationHelper.notifyMessage(
                context,
                title = "MMS reçu",
                preview = "Le contenu MMS a été conservé localement pour traitement. La présentation des pièces jointes reste en validation.",
                notificationId = digest.hashCode()
            )
        }
    }

    private fun prune(directory: File) {
        val files = directory.listFiles()?.sortedByDescending { it.lastModified() }.orEmpty()
        files.drop(MAX_STORED_MMS - 1).forEach { runCatching { it.delete() } }
    }

    companion object {
        private const val MAX_PDU_BYTES = 512 * 1024
        private const val MAX_STORED_MMS = 50
    }
}
