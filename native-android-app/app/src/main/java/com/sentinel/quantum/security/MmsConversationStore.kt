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
                    rootInsertCleanupFailed = !cleanupExactUri(uri, transactionId)
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
                cleanupRoot(providerMessageId, insertedRootUri, transactionId)

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

    fun recordRejectedBeforeTransport(token: String, providerMessageId: Long): Boolean {
        val nowMs = System.currentTimeMillis()
        val transactionId = journal.read(token)?.transactionId ?: return false
        if (!journal.markRejectedBeforeTransport(token, providerMessageId, nowMs)) return false
        if (holdsSmsRole() && transitionMessageBox(
                providerMessageId,
                transactionId,
                successful = false,
                nowMs = nowMs
            )
        ) {
            journal.remove(token)
        }
        return true
    }

    fun abandonBeforeTransport(token: String, providerMessageId: Long): Boolean {
        if (!holdsSmsRole() || !MmsProviderJournal.validToken(token) || providerMessageId <= 0L) return false
        val record = journal.read(token) ?: return false
        if (record.providerMessageId != null && record.providerMessageId != providerMessageId) return false
        val deleted = cleanupRoot(providerMessageId, null, record.transactionId)
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
        val record = journal.read(token) ?: return false
        if (record.providerMessageId != null && record.providerMessageId != providerMessageId) return false
        val outcomeJournaled = journal.markResult(token, providerMessageId, successful, nowMs)
        val updated = transitionMessageBox(providerMessageId, record.transactionId, successful, nowMs)
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
            when (MmsOwnedRootMutation.repairAction(policyPhase(record.phase), roleHeld = true)) {
                MmsOwnedRootMutation.RepairAction.KEEP_ROW,
                MmsOwnedRootMutation.RepairAction.RETRY_LATER -> Unit
                MmsOwnedRootMutation.RepairAction.DELETE_OWNED_INCOMPLETE -> {
                    when (record.phase) {
                        MmsProviderJournal.Phase.BUILDING -> {
                            when (val lookup = lookupRoot(record.transactionId)) {
                                is RootLookup.Found -> if (
                                    cleanupRoot(lookup.id, lookup.uri, record.transactionId) &&
                                    journal.remove(record.token)
                                ) repaired++
                                RootLookup.Absent -> if (journal.remove(record.token)) repaired++
                                RootLookup.Error -> Unit
                            }
                        }
                        MmsProviderJournal.Phase.ROOT_INSERTED -> {
                            val id = record.providerMessageId ?: continue
                            if (cleanupRoot(id, null, record.transactionId) && journal.remove(record.token)) {
                                repaired++
                            }
                        }
                        else -> Unit
                    }
                }
                MmsOwnedRootMutation.RepairAction.FINALIZE -> {
                    val id = record.providerMessageId ?: continue
                    val successful = record.phase == MmsProviderJournal.Phase.RESULT_SENT
                    if (
                        transitionMessageBox(id, record.transactionId, successful, record.updatedAtMs) &&
                        journal.remove(record.token)
                    ) repaired++
                }
            }
        }
        return repaired
    }

    private fun transitionMessageBox(
        providerMessageId: Long,
        transactionId: String,
        successful: Boolean,
        nowMs: Long
    ): Boolean {
        if (providerMessageId <= 0L || nowMs < 0L || transactionId.isBlank()) return false
        val expected = MmsOwnedRootMutation.OutgoingIdentity(
            providerMessageId,
            appContext.packageName,
            transactionId
        )
        val observed = when (val read = readOutgoing(providerMessageId)) {
            OutgoingRead.Error -> return false
            OutgoingRead.Absent -> null
            is OutgoingRead.Row -> read.observed
        }
        val decision = MmsOwnedRootMutation.authorizeOutgoingFinalize(
            roleHeld = holdsSmsRole(),
            expected = expected,
            successful = successful,
            observed = observed
        )
        val uri = ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, providerMessageId)
        val target = if (successful) Telephony.Mms.MESSAGE_BOX_SENT else Telephony.Mms.MESSAGE_BOX_FAILED
        return MmsOwnedRootMutation.applyFinalize(
            decision = decision,
            targetBox = MmsOwnedRootMutation.targetBox(successful),
            updateMatching = { clauses ->
                val (selection, args) = selection(clauses)
                val values = ContentValues().apply {
                    put(Telephony.Mms.MESSAGE_BOX, target)
                    if (successful) put(Telephony.Mms.DATE_SENT, nowMs / 1000L)
                }
                runCatching { appContext.contentResolver.update(uri, values, selection, args) }
                    .getOrDefault(-1)
            },
            reread = { observedOrNull(readOutgoing(providerMessageId)) }
        )
    }

    private fun cleanupRoot(providerMessageId: Long, knownUri: Uri?, transactionId: String): Boolean {
        val expected = MmsOwnedRootMutation.OutgoingIdentity(
            providerMessageId,
            appContext.packageName,
            transactionId
        )
        val observed = when (val read = readOutgoing(providerMessageId)) {
            OutgoingRead.Error -> return false
            OutgoingRead.Absent -> null
            is OutgoingRead.Row -> read.observed
        }
        val decision = MmsOwnedRootMutation.authorizeOutgoingDelete(
            roleHeld = holdsSmsRole(),
            expected = expected,
            observed = observed
        )
        val uri = knownUri ?: ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, providerMessageId)
        return deleteAuthorized(uri, decision) { readOutgoing(providerMessageId) }
    }

    private fun cleanupExactUri(uri: Uri, transactionId: String): Boolean {
        if (!holdsSmsRole() || transactionId.isBlank()) return false
        val read = readOutgoingUri(uri)
        val observed = when (read) {
            OutgoingRead.Error -> return false
            OutgoingRead.Absent -> null
            is OutgoingRead.Row -> read.observed
        }
        val providerMessageId = observed?.id ?: return observed == null
        val decision = MmsOwnedRootMutation.authorizeOutgoingDelete(
            roleHeld = true,
            expected = MmsOwnedRootMutation.OutgoingIdentity(
                providerMessageId,
                appContext.packageName,
                transactionId
            ),
            observed = observed
        )
        return deleteAuthorized(uri, decision) { readOutgoingUri(uri) }
    }

    private fun deleteAuthorized(
        uri: Uri,
        decision: MmsOwnedRootMutation.Decision,
        reread: () -> OutgoingRead
    ): Boolean = MmsOwnedRootMutation.applyDelete(
        decision = decision,
        deleteMatching = { clauses ->
            val (selection, args) = selection(clauses)
            runCatching { appContext.contentResolver.delete(uri, selection, args) }.getOrDefault(-1)
        },
        reread = { observedOrNull(reread()) }
    )

    private fun observedOrNull(read: OutgoingRead): MmsOwnedRootMutation.Observed? = when (read) {
        OutgoingRead.Absent -> null
        OutgoingRead.Error -> MmsOwnedRootMutation.Observed(
            id = -1L,
            creator = null,
            messageBox = -1,
            transactionId = null
        )
        is OutgoingRead.Row -> read.observed
    }

    private sealed interface OutgoingRead {
        data class Row(val observed: MmsOwnedRootMutation.Observed) : OutgoingRead
        data object Absent : OutgoingRead
        data object Error : OutgoingRead
    }

    private fun readOutgoing(providerMessageId: Long): OutgoingRead {
        if (providerMessageId <= 0L) return OutgoingRead.Error
        return readOutgoingUri(ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, providerMessageId))
    }

    private fun readOutgoingUri(uri: Uri): OutgoingRead = runCatching {
        appContext.contentResolver.query(
            uri,
            arrayOf(
                Telephony.Mms._ID,
                Telephony.Mms.CREATOR,
                Telephony.Mms.MESSAGE_BOX,
                Telephony.Mms.TRANSACTION_ID
            ),
            null,
            null,
            null
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use OutgoingRead.Absent
            OutgoingRead.Row(
                MmsOwnedRootMutation.Observed(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Mms._ID)),
                    creator = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Mms.CREATOR)),
                    messageBox = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Mms.MESSAGE_BOX)),
                    transactionId = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Mms.TRANSACTION_ID))
                )
            )
        } ?: OutgoingRead.Error
    }.getOrDefault(OutgoingRead.Error)

    private fun selection(clauses: List<MmsOwnedRootMutation.Clause>): Pair<String, Array<String>> {
        val parts = ArrayList<String>(clauses.size)
        val args = ArrayList<String>(clauses.size)
        for (clause in clauses) {
            val name = when (clause.column) {
                MmsOwnedRootMutation.Column.ID -> Telephony.Mms._ID
                MmsOwnedRootMutation.Column.BOX -> Telephony.Mms.MESSAGE_BOX
                MmsOwnedRootMutation.Column.CREATOR -> Telephony.Mms.CREATOR
                MmsOwnedRootMutation.Column.TRANSACTION -> Telephony.Mms.TRANSACTION_ID
                MmsOwnedRootMutation.Column.MESSAGE_ID -> Telephony.Mms.MESSAGE_ID
                MmsOwnedRootMutation.Column.DATE -> Telephony.Mms.DATE
                MmsOwnedRootMutation.Column.SUBSCRIPTION -> Telephony.Mms.SUBSCRIPTION_ID
            }
            if (clause.isNull) {
                parts += "$name IS NULL"
            } else {
                parts += "$name=?"
                args += clause.value.orEmpty()
            }
        }
        return parts.joinToString(separator = " AND ") to args.toTypedArray()
    }

    private fun policyPhase(phase: MmsProviderJournal.Phase): MmsOwnedRootMutation.OutgoingPhase =
        when (phase) {
            MmsProviderJournal.Phase.BUILDING -> MmsOwnedRootMutation.OutgoingPhase.BUILDING
            MmsProviderJournal.Phase.ROOT_INSERTED -> MmsOwnedRootMutation.OutgoingPhase.ROOT_INSERTED
            MmsProviderJournal.Phase.READY -> MmsOwnedRootMutation.OutgoingPhase.READY
            MmsProviderJournal.Phase.SUBMITTED -> MmsOwnedRootMutation.OutgoingPhase.SUBMITTED
            MmsProviderJournal.Phase.SUBMISSION_UNKNOWN ->
                MmsOwnedRootMutation.OutgoingPhase.SUBMISSION_UNKNOWN
            MmsProviderJournal.Phase.RESULT_SENT -> MmsOwnedRootMutation.OutgoingPhase.RESULT_SENT
            MmsProviderJournal.Phase.RESULT_FAILED -> MmsOwnedRootMutation.OutgoingPhase.RESULT_FAILED
            MmsProviderJournal.Phase.REJECTED_BEFORE_TRANSPORT ->
                MmsOwnedRootMutation.OutgoingPhase.REJECTED_BEFORE_TRANSPORT
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
