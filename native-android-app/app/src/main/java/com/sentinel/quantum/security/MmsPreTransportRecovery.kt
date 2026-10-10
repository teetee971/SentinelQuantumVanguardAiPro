package com.sentinel.quantum.security

import android.content.ContentUris
import android.content.Context
import android.provider.Telephony

/** Fail-closed recovery for MMS records that are durably proven not to have entered transport. */
internal object MmsPreTransportRecovery {
    fun repairReadyRecords(context: Context): Boolean {
        val appContext = context.applicationContext
        val journal = MmsProviderJournal(appContext)
        val ready = journal.all().filter { it.phase == MmsProviderJournal.Phase.READY }
        if (ready.isEmpty()) return true
        if (
            appContext.readSmsRoleStateFailClosed() !=
                SmsActivationDiagnostics.SmsRoleState.HELD
        ) return false

        var complete = true
        ready.forEach { record ->
            val id = record.providerMessageId
            if (id == null || id <= 0L) {
                complete = false
                return@forEach
            }
            val uri = ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, id)
            val removed = runCatching {
                val deleted = appContext.contentResolver.delete(uri, null, null)
                if (deleted > 0) true else {
                    appContext.contentResolver.query(
                        uri,
                        arrayOf(Telephony.Mms._ID),
                        null,
                        null,
                        null
                    )?.use { !it.moveToFirst() } ?: false
                }
            }.getOrDefault(false)
            val pduRemoved = MmsSendPduStager.delete(appContext, "${record.token}.pdu")
            if (removed && pduRemoved) {
                if (!journal.remove(record.token)) complete = false
            } else {
                complete = false
            }
        }
        return complete
    }
}
