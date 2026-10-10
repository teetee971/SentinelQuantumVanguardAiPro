package com.sentinel.quantum.security

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.Telephony

/**
 * Default-SMS-app projection of outgoing MMS messages into Android's Telephony MMS provider.
 *
 * The carrier callback and this provider projection are independent truths. Provider write failure
 * is never converted into carrier success, and a carrier success never proves recipient delivery.
 */
internal class MmsConversationStore(private val context: Context) {
    data class Attachment(val mimeType: String, val payload: ByteArray)

    sealed interface PersistResult {
        data class Ready(val providerMessageId: Long) : PersistResult
        data class Rejected(val reason: String, val cleanupConfirmed: Boolean) : PersistResult
    }

    private val appContext = context.applicationContext
    private val journal = MmsProviderJournal(appContext)

    fun persistOutgoingOutbox(
        token: String,
        transactionId: String,
        destination: String,
        text: String,
        attachments: List<Attachment>,
        subscriptionId: Int,
        nowMs: Long = System.currentTimeMillis()
    ): PersistResult {
        if (!holdsSmsRole()) return PersistResult.Rejected("SMS_ROLE_NOT_HELD", true)
        if (!MmsProviderJournal.validToken(token) || !MmsProviderJournal.validTransactionId(transactionId)) {
            return PersistResult.Rejected("MMS_PROVIDER_CORRELATION_INVALID", true)
        }
        val normalized = CallRuleEngine.normalizeNumber(destination)
            ?: return PersistResult.Rejected("INVALID_DESTINATION", true)
        if (normalized != destination || subscriptionId < 0 || nowMs < 0L) {
            return PersistResult.Rejected("MMS_PROVIDER_INPUT_INVALID", true)
        }
        if (text.length > MmsSendEligibilityPolicy.MAX_TEXT_CHARS || attachments.size > MmsSendEligibilityPolicy.MAX_ATTACHMENTS) {
            return PersistResult.Rejected("MMS_PROVIDER_INPUT_INVALID", true)
        }
        if (text.isBlank() && attachments.isEmpty()) {
            return PersistResult.Rejected("EMPTY_MMS", true)
        }
        var payloadBytes = text.toByteArray(Charsets.UTF_8).size.toLong()
        for (attachment in attachments) {
            if (
                attachment.mimeType.lowercase() !in SUPPORTED_MIME_TYPES ||
                attachment.payload.isEmpty() ||
                attachment.payload.size.toLong() > MmsSendEligibilityPolicy.MAX_ATTACHMENT_BYTES ||
                Long.MAX_VALUE - payloadBytes < attachment.payload.size.toLong()
            ) return PersistResult.Rejected("MMS_PROVIDER_INPUT_INVALID", true)
            payloadBytes += attachment.payload.size.toLong()
            if (payloadBytes > MmsSendEligibilityPolicy.MAX_TOTAL_ATTACHMENT_BYTES +
                MmsSendEligibilityPolicy.MAX_TEXT_CHARS * 4L) {
                return PersistResult.Rejected("MMS_PROVIDER_INPUT_INVALID", true)
            }
        }

        var insertedRootUri: Uri? = null
        var rootInsertCleanupFailed = false
        val operations = object : MmsProviderProjectionTransaction.Operations {
            override fun beginJournal(): Boolean = journal.begin(token, transactionId, nowMs)

            override fun insertRoot(): Long? {
                val threadId = runCatching {
                    Telephony.Threads.getOrCreateThreadId(appContext, normalized)
                }.getOrNull()?.takeIf { it > 0L } ?: return null

                val values = ContentValues().apply {
                    put(Telephony.Mms.THREAD_ID, threadId)
                    put(Telephony.Mms.DATE, nowMs / 1000L)
                    put(Telephony.Mms.DATE_SENT, 0L)
                    put(Telephony.Mms.READ, 1)
                    put(Telephony.Mms.SEEN, 1)
                    put(Telephony.Mms.MESSAGE_TYPE, MESSAGE_TYPE_SEND_REQ)
                    put(Telephony.Mms.MMS_VERSION, MMS_VERSION_1_2)
                    put(Telephony.Mms.CONTENT_TYPE, ROOT_CONTENT_TYPE)
                    put(Telephony.Mms.TRANSACTION_ID, transactionId)
                    put(Telephony.Mms.MESSAGE_SIZE, payloadBytes)
                    put(Telephony.Mms.TEXT_ONLY, if (attachments.isEmpty()) 1 else 0)
                    put(Telephony.Mms.SUBSCRIPTION_ID, subscriptionId)
                }
                val uri = runCatching {
                    appContext.contentResolver.insert(Telephony.Mms.Outbox.CONTENT_URI, values)
                }.getOrNull() ?: return null
                insertedRootUri = uri

                val id = runCatching { ContentUris.parseId(uri) }.getOrNull()?.takeIf { it > 0L }
                if (id == null) {
                    // A non-null insert URI proves that provider mutation may have happened. Do not
                    // erase the recovery journal unless that exact URI is confirmed absent again.
                    rootInsertCleanupFailed = !cleanupExactUri(uri)
                    return null
                }
                return id
            }

            override fun recordRoot(providerMessageId: Long): Boolean =
                journal.recordRoot(token, providerMessageId)

            override fun insertAddress(providerMessageId: Long): Boolean {
                val values = ContentValues().apply {
                    put(Telephony.Mms.Addr.ADDRESS, normalized)
                    put(Telephony.Mms.Addr.CHARSET, UTF_8_MIB_ENUM)
                    put(Telephony.Mms.Addr.TYPE, ADDRESS_TYPE_TO)
                }
                return runCatching {
                    appContext.contentResolver.insert(addressUri(providerMessageId), values) != null
                }.getOrDefault(false)
            }

            override fun insertParts(providerMessageId: Long): Boolean {
                var sequence = 0
                if (text.isNotEmpty()) {
                    val values = ContentValues().apply {
                        put(Telephony.Mms.Part.SEQ, sequence++)
                        put(Telephony.Mms.Part.CONTENT_TYPE, "text/plain")
                        put(Telephony.Mms.Part.CHARSET, UTF_8_MIB_ENUM)
                        put(Telephony.Mms.Part.TEXT, text)
                    }
                    if (runCatching {
                            appContext.contentResolver.insert(partsUri(providerMessageId), values)
                        }.getOrNull() == null) return false
                }

                for (attachment in attachments) {
                    val values = ContentValues().apply {
                        put(Telephony.Mms.Part.SEQ, sequence++)
                        put(Telephony.Mms.Part.CONTENT_TYPE, attachment.mimeType.lowercase())
                    }
                    val partUri = runCatching {
                        appContext.contentResolver.insert(partsUri(providerMessageId), values)
                    }.getOrNull() ?: return false
                    val written = runCatching {
                        appContext.contentResolver.openOutputStream(partUri, "w")?.use { output ->
                            output.write(attachment.payload)
                            output.flush()
                            true
                        } ?: false
                    }.getOrDefault(false)
                    if (!written) return false
                }
                return sequence > 0
            }

            override fun markReady(providerMessageId: Long): Boolean =
                journal.markReady(token, providerMessageId)

            override fun deleteRoot(providerMessageId: Long): Boolean =
                cleanupRoot(providerMessageId, insertedRootUri)

            override fun clearJournal(): Boolean =
                !rootInsertCleanupFailed && journal.remove(token)
        }

        return when (val result = MmsProviderProjectionTransaction.execute(operations)) {
            is MmsProviderProjectionTransaction.Result.Ready ->
                PersistResult.Ready(result.providerMessageId)
            is MmsProviderProjectionTransaction.Result.Rejected ->
                PersistResult.Rejected(result.reason, result.cleanupConfirmed)
        }
    }

    fun markSubmitted(token: String, providerMessageId: Long): Boolean =
        journal.markSubmitted(token, providerMessageId)

    fun markSubmissionUnknown(token: String, providerMessageId: Long): Boolean =
        journal.markSubmissionUnknown(token, providerMessageId)

    fun abandonBeforeTransport(token: String, providerMessageId: Long): Boolean {
        if (!holdsSmsRole() || !MmsProviderJournal.validToken(token) || providerMessageId <= 0L) return false
        val deleted = cleanupRoot(providerMessageId, null)
        return deleted && journal.remove(token)
    }

    /**
     * Persist an Android/carrier transport callback into the provider projection.
     * The outcome is journaled first so a failed provider update is recoverable after process death.
     */
    fun applyTransportResult(
        token: String,
        providerMessageId: Long,
        successful: Boolean,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        if (!holdsSmsRole() || !MmsProviderJournal.validToken(token) || providerMessageId <= 0L) return false
        val outcomeJournaled = journal.markResult(token, providerMessageId, successful, nowMs)
        val updated = transitionMessageBox(providerMessageId, successful, nowMs)
        if (updated) {
            // A stale journal record is safe but noisy. Do not downgrade a confirmed provider write
            // if cleanup metadata itself is unavailable.
            journal.remove(token)
        } else if (!outcomeJournaled) {
            LocalLogger(appContext).log(
                LocalLogger.LogLevel.WARNING,
                "MmsProvider",
                "Résultat MMS non projeté et journal de réparation indisponible"
            )
        }
        return updated
    }

    /** Best-effort repair of incomplete builds and callback projections; never invents carrier proof. */
    fun repairJournal(): Int {
        if (!holdsSmsRole()) return 0
        var repaired = 0
        for (record in journal.all()) {
            when (record.phase) {
                MmsProviderJournal.Phase.BUILDING -> {
                    when (val lookup = lookupRoot(record.transactionId)) {
                        is RootLookup.Found -> if (cleanupRoot(lookup.id, lookup.uri) && journal.remove(record.token)) repaired++
                        RootLookup.Absent -> if (journal.remove(record.token)) repaired++
                        RootLookup.Error -> Unit
                    }
                }
                MmsProviderJournal.Phase.ROOT_INSERTED -> {
                    val id = record.providerMessageId ?: continue
                    if (cleanupRoot(id, null) && journal.remove(record.token)) repaired++
                }
                MmsProviderJournal.Phase.RESULT_SENT,
                MmsProviderJournal.Phase.RESULT_FAILED -> {
                    val id = record.providerMessageId ?: continue
                    val successful = record.phase == MmsProviderJournal.Phase.RESULT_SENT
                    if (transitionMessageBox(id, successful, record.updatedAtMs) && journal.remove(record.token)) repaired++
                }
                MmsProviderJournal.Phase.READY,
                MmsProviderJournal.Phase.TRANSPORT_STARTED,
                MmsProviderJournal.Phase.SUBMITTED,
                MmsProviderJournal.Phase.SUBMISSION_UNKNOWN -> Unit
            }
        }
        return repaired
    }

    private fun transitionMessageBox(providerMessageId: Long, successful: Boolean, nowMs: Long): Boolean {
        if (providerMessageId <= 0L || nowMs < 0L) return false
        val values = ContentValues().apply {
            put(
                Telephony.Mms.MESSAGE_BOX,
                if (successful) Telephony.Mms.MESSAGE_BOX_SENT else Telephony.Mms.MESSAGE_BOX_FAILED
            )
            if (successful) put(Telephony.Mms.DATE_SENT, nowMs / 1000L)
        }
        val uri = ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, providerMessageId)
        return runCatching {
            appContext.contentResolver.update(uri, values, null, null) == 1
        }.getOrDefault(false)
    }

    private fun cleanupRoot(providerMessageId: Long, knownUri: Uri?): Boolean {
        val uri = knownUri ?: ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, providerMessageId)
        val deleted = runCatching { appContext.contentResolver.delete(uri, null, null) }.getOrNull()
            ?: return false
        if (deleted > 0) return true
        return !rootExists(providerMessageId)
    }

    private fun cleanupExactUri(uri: Uri): Boolean {
        val deleted = runCatching { appContext.contentResolver.delete(uri, null, null) }.getOrNull()
            ?: return false
        if (deleted > 0) return true
        return runCatching {
            appContext.contentResolver.query(uri, arrayOf(Telephony.Mms._ID), null, null, null)
                ?.use { !it.moveToFirst() }
                ?: false
        }.getOrDefault(false)
    }

    private fun rootExists(providerMessageId: Long): Boolean {
        val uri = ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, providerMessageId)
        return runCatching {
            appContext.contentResolver.query(
                uri,
                arrayOf(Telephony.Mms._ID),
                null,
                null,
                null
            )?.use { it.moveToFirst() } ?: true
        }.getOrDefault(true)
    }

    private sealed interface RootLookup {
        data class Found(val id: Long, val uri: Uri) : RootLookup
        data object Absent : RootLookup
        data object Error : RootLookup
    }

    private fun lookupRoot(transactionId: String): RootLookup = runCatching {
        appContext.contentResolver.query(
            Telephony.Mms.CONTENT_URI,
            arrayOf(Telephony.Mms._ID, Telephony.Mms.CREATOR),
            "${Telephony.Mms.TRANSACTION_ID}=? AND ${Telephony.Mms.MESSAGE_BOX}=?",
            arrayOf(transactionId, Telephony.Mms.MESSAGE_BOX_OUTBOX.toString()),
            null
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(Telephony.Mms._ID)
            val creatorIndex = cursor.getColumnIndexOrThrow(Telephony.Mms.CREATOR)
            var match: Long? = null
            while (cursor.moveToNext()) {
                val creator = cursor.getString(creatorIndex)
                if (creator != appContext.packageName) continue
                val id = cursor.getLong(idIndex).takeIf { it > 0L } ?: continue
                if (match != null && match != id) return@use RootLookup.Error
                match = id
            }
            match?.let {
                RootLookup.Found(it, ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, it))
            } ?: RootLookup.Absent
        } ?: RootLookup.Error
    }.getOrDefault(RootLookup.Error)

    private fun addressUri(providerMessageId: Long): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Telephony.Mms.Addr.getAddrUriForMessage(providerMessageId.toString())
        } else {
            Uri.parse("content://mms/$providerMessageId/addr")
        }

    private fun partsUri(providerMessageId: Long): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Telephony.Mms.Part.getPartUriForMessage(providerMessageId.toString())
        } else {
            Uri.parse("content://mms/$providerMessageId/part")
        }

    private fun holdsSmsRole(): Boolean =
        appContext.readSmsRoleStateFailClosed() == SmsActivationDiagnostics.SmsRoleState.HELD

    companion object {
        private const val MESSAGE_TYPE_SEND_REQ = 0x80
        private const val MMS_VERSION_1_2 = 0x12
        private const val ADDRESS_TYPE_TO = 0x97
        private const val UTF_8_MIB_ENUM = 106
        private const val ROOT_CONTENT_TYPE = "application/vnd.wap.multipart.mixed"
        private val SUPPORTED_MIME_TYPES = setOf(
            "text/plain", "image/jpeg", "image/png", "image/gif", "image/webp"
        )
    }
}
