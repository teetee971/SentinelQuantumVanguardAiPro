package com.sentinel.quantum.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import java.io.File

/**
 * Receives WAP/MMS delivery only when Sentinel is the default SMS handler.
 *
 * Full MMS rendering is not claimed yet. The bounded raw payload is retained in app-private
 * storage instead of being silently discarded, so device testing can validate the migration
 * path before public release.
 */
class MmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
        if (!SmsRepository(context).isDefaultHandler()) return

        val payload = intent.getByteArrayExtra("data") ?: return
        if (payload.isEmpty() || payload.size > MAX_PDU_BYTES) return

        runCatching {
            val directory = File(context.filesDir, "pending_mms").apply { mkdirs() }
            val target = File(directory, "mms-${System.currentTimeMillis()}.pdu")
            target.outputStream().use { it.write(payload) }
            prune(directory)
        }
    }

    private fun prune(directory: File) {
        val files = directory.listFiles()?.sortedByDescending(File::lastModified).orEmpty()
        files.drop(MAX_PENDING).forEach { runCatching { it.delete() } }
    }

    private companion object {
        const val MAX_PDU_BYTES = 2 * 1024 * 1024
        const val MAX_PENDING = 25
    }
}
