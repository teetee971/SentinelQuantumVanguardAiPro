package com.sentinel.quantum.security

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.Telephony

/**
 * Default-SMS-app projection of a downloaded incoming MMS into Android's Telephony MMS provider.
 *
 * Provider identity is fail-closed: a root is never created without a verified international sender,
 * the real carrier Transaction-ID and an owned SMS role. SAFE parts may be copied to the provider;
 * QUARANTINED content creates only root + FROM metadata so unsafe bytes are never exposed as message
 * content. The private raw PDU remains the quarantine source of truth.
 */
internal class IncomingMmsConversationStore(private val context: Context) {
    sealed interface PersistResult {
        data class Ready(
            val providerMessageId: Long,
            val contentState: IncomingMmsProviderJournal.ContentState
        ) : PersistResult

        data class Duplicate(
            val providerMessageId: Long,
            val contentState: IncomingMmsProviderJournal.ContentState
        ) : PersistResult

        data class Rejected(val reason: String, val cleanupConfirmed: Boolean) : PersistResult
    }

    private sealed interface ExistingResolution {
        data object Continue : ExistingResolution
        data class Duplicate(
            val providerMessageId: Long,
            val contentState: IncomingMmsProviderJournal.ContentState
        ) : ExistingResolution
        data class Blocked(val reason: String, val cleanupConfirmed: Boolean) : ExistingResolution
    }

    private sealed interface RootLookup {
        data class Found(val id: Long, val uri: Uri) : RootLookup
        data object Absent : RootLookup
        data object Error : RootLookup
    }

    private enum class RootPresence { PRESENT, ABSENT, ERROR }

    private val appContext = context.applicationContext
    private val journal = IncomingMmsProviderJournal(appContext)

    fun persistDownloadedInbox(
        fingerprint: String,
        transactionId: String,
        sender: String,
        subscriptionId: Int,
        rawPduSize: Long,
        preview: MmsDecodePipeline.Result,
        nowMs: Long = System.currentTimeMillis()
    ): PersistResult {
        if (!holdsSmsRole()) return PersistResult.Rejected("SMS_ROLE_NOT_HELD", true)
        if (
            !IncomingMmsProviderJournal.validFingerprint(fingerprint) ||
            !IncomingMmsProviderJournal.validTransactionId(transactionId) ||
            subscriptionId < 0 ||
            rawPduSize !in 1..MmsDownloadCoordinator.MAX_DOWNLOADED_PDU_BYTES ||
            nowMs < 0L
        ) return PersistResult.Rejected("MMS_INCOMING_PROVIDER_INPUT_INVALID", true)

        val normalized = CallRuleEngine.normalizeNumber(sender)
            ?: return PersistResult.Rejected("MMS_INCOMING_SENDER_INVALID", true)
        if (normalized != sender || !normalized.startsWith('+')) {
            return PersistResult.Rejected("MMS_INCOMING_SENDER_INVALID", true)
        }

        val threadId = runCatching {
            Telephony.Threads.getOrCreateThreadId(appContext, normalized)
        }.getOrNull()?.takeIf { it > 0L }
            ?: return PersistResult.Rejected("MMS_PROVIDER_THREAD_RESOLUTION_FAILED", true)

        val state: IncomingMmsProviderJournal.ContentState
        val safeParts: List<MmsDecodeBoundary.SafePart>
        when (preview) {
            is MmsDecodePipeline.Result.Accepted -> {
                val revalidated = MmsDecodeBoundary.validate(
                    preview.parts.map {
                        MmsDecodeBoundary.DecodedPart(it.mimeType, it.fileName, it.payload)
                    }
                )
                if (revalidated !is MmsDecodeBoundary.Result.Accepted) {
                    return PersistResult.Rejected("MMS_SAFE_PART_REVALIDATION_FAILED", true)
                }
                state = IncomingMmsProviderJournal.ContentState.SAFE
                safeParts = revalidated.parts
            }
            is MmsDecodePipeline.Result.Rejected -> {
                state = IncomingMmsProviderJournal.ContentState.QUARANTINED
                safeParts = emptyList()
            }
        }

        when (
            val existing = reconcileExisting(
                fingerprint = fingerprint,
                transactionId = transactionId,
                threadId = threadId,
                subscriptionId = subscriptionId
            )
        ) {
            ExistingResolution.Continue -> Unit
            is ExistingResolution.Duplicate -> return PersistResult.Duplicate(
                existing.providerMessageId,
                existing.contentState
            )
            is ExistingResolution.Blocked -> return PersistResult.Rejected(
                existing.reason,
                existing.cleanupConfirmed
            )
        }

        var insertedRootUri: Uri? = null
        var rootInsertCleanupFailed = false
        val operations = object : MmsProviderProjectionTransaction.Operations {
            override fun beginJournal(): Boolean = journal.begin(
                fingerprint = fingerprint,
                transactionId = transactionId,
                threadId = threadId,
                subscriptionId = subscriptionId,
                contentState = state,
                nowMs = nowMs
            )

            override fun insertRoot(): Long? {
                val values = ContentValues().apply {
                    put(Telephony.Mms.THREAD_ID, threadId)
                    put(Telephony.Mms.DATE, nowMs / 1000L)
                    put(Telephony.Mms.DATE_SENT, 0L)
                    put(Telephony.Mms.READ, 0)
                    put(Telephony.Mms.SEEN, 0)
                    put(Telephony.Mms.MESSAGE_TYPE, MESSAGE_TYPE_RETRIEVE_CONF)
                    put(Telephony.Mms.MMS_VERSION, MMS_VERSION_1_2)
                    put(Telephony.Mms.CONTENT_TYPE, ROOT_CONTENT_TYPE)
                    put(Telephony.Mms.TRANSACTION_ID, transactionId)
                    put(Telephony.Mms.MESSAGE_SIZE, rawPduSize)
                    put(
                        Telephony.Mms.TEXT_ONLY,
                        if (state == IncomingMmsProviderJournal.ContentState.SAFE &&
                            safeParts.all { it.mimeType == "text/plain" }) 1 else 0
                    )
                    put(Telephony.Mms.SUBSCRIPTION_ID, subscriptionId)
                    put(Telephony.Mms.CREATOR, appContext.packageName)
                }
                val uri = runCatching {
                    appContext.contentResolver.insert(Telephony.Mms.Inbox.CONTENT_URI, values)
                }.getOrNull() ?: return null
                insertedRootUri = uri

                val id = runCatching { ContentUris.parseId(uri) }.getOrNull()?.takeIf { it > 0L }
                if (id == null) {
                    rootInsertCleanupFailed = !cleanupExactUri(uri)
                    return null
                }
                return id
            }

            override fun recordRoot(providerMessageId: Long): Boolean =
                journal.recordRoot(fingerprint, providerMessageId)

            override fun insertAddress(providerMessageId: Long): Boolean {
                val values = ContentValues().apply {
                    put(Telephony.Mms.Addr.ADDRESS, normalized)
                    put(Telephony.Mms.Addr.CHARSET, UTF_8_MIB_ENUM)
                    put(Telephony.Mms.Addr.TYPE, ADDRESS_TYPE_FROM)
                }
                return runCatching {
                    appContext.contentResolver.insert(addressUri(providerMessageId), values) != null
                }.getOrDefault(false)
            }

            override fun insertParts(providerMessageId: Long): Boolean {
                if (state == IncomingMmsProviderJournal.ContentState.QUARANTINED) return true
                var sequence = 0
                for (part in safeParts) {
                    if (part.mimeType == "text/plain") {
                        val text = part.payload.toString(Charsets.UTF_8)
                        val values = ContentValues().apply {
                            put(Telephony.Mms.Part.SEQ, sequence++)
                            put(Telephony.Mms.Part.CONTENT_TYPE, "text/plain")
                            put(Telephony.Mms.Part.CHARSET, UTF_8_MIB_ENUM)
                            part.fileName?.let { put(Telephony.Mms.Part.NAME, it) }
                            put(Telephony.Mms.Part.TEXT, text)
                        }
                        if (runCatching {
                                appContext.contentResolver.insert(partsUri(providerMessageId), values)
                            }.getOrNull() == null) return false
                        continue
                    }

                    val values = ContentValues().apply {
                        put(Telephony.Mms.Part.SEQ, sequence++)
                        put(Telephony.Mms.Part.CONTENT_TYPE, part.mimeType)
                        part.fileName?.let {
                            put(Telephony.Mms.Part.NAME, it)
                            put(Telephony.Mms.Part.CONTENT_LOCATION, it)
                        }
                    }
                    val partUri = runCatching {
                        appContext.contentResolver.insert(partsUri(providerMessageId), values)
                    }.getOrNull() ?: return false
                    val written = runCatching {
                        appContext.contentResolver.openOutputStream(partUri, "w")?.use { output ->
                            output.write(part.payload)
                            output.flush()
                            true
                        } ?: false
                    }.getOrDefault(false)
                    if (!written) return false
                }
                return sequence == safeParts.size && sequence > 0
            }

            override fun markReady(providerMessageId: Long): Boolean =
                journal.markReady(fingerprint, providerMessageId)

            override fun deleteRoot(providerMessageId: Long): Boolean =
                cleanupRoot(providerMessageId, insertedRootUri)

            override fun clearJournal(): Boolean =
                !rootInsertCleanupFailed && journal.remove(fingerprint)
        }

        return when (val result = MmsProviderProjectionTransaction.execute(operations)) {
            is MmsProviderProjectionTransaction.Result.Ready ->
                PersistResult.Ready(result.providerMessageId, state)
            is MmsProviderProjectionTransaction.Result.Rejected ->
                PersistResult.Rejected(result.reason, result.cleanupConfirmed)
        }
    }

    /**
     * Reconcile partial provider builds after process death. READY rows are retained for bounded
     * duplicate suppression while the corresponding provider root exists.
     */
    fun repairJournal(): Int {
        if (!holdsSmsRole()) return 0
        var repaired = 0
        for (record in journal.all()) {
            when (record.phase) {
                IncomingMmsProviderJournal.Phase.BUILDING -> {
                    when (
                        val lookup = lookupRoot(
                            transactionId = record.transactionId,
                            threadId = record.threadId,
                            subscriptionId = record.subscriptionId
                        )
                    ) {
                        is RootLookup.Found -> {
                            if (cleanupRoot(lookup.id, lookup.uri) && journal.remove(record.fingerprint)) {
                                repaired++
                            }
                        }
                        RootLookup.Absent -> if (journal.remove(record.fingerprint)) repaired++
                        RootLookup.Error -> Unit
                    }
                }
                IncomingMmsProviderJournal.Phase.ROOT_INSERTED -> {
                    val id = record.providerMessageId ?: continue
                    if (cleanupRoot(id, null) && journal.remove(record.fingerprint)) repaired++
                }
                IncomingMmsProviderJournal.Phase.READY -> {
                    val id = record.providerMessageId ?: continue
                    if (rootPresence(id) == RootPresence.ABSENT && journal.remove(record.fingerprint)) {
                        repaired++
                    }
                }
            }
        }
        journal.pruneExpiredReady()
        return repaired
    }

    fun contentStateForProviderMessage(providerMessageId: Long): IncomingMmsProviderJournal.ContentState? {
        if (providerMessageId <= 0L) return null
        return journal.all()
            .firstOrNull {
                it.phase == IncomingMmsProviderJournal.Phase.READY &&
                    it.providerMessageId == providerMessageId
            }
            ?.contentState
    }

    private fun reconcileExisting(
        fingerprint: String,
        transactionId: String,
        threadId: Long,
        subscriptionId: Int
    ): ExistingResolution {
        val record = journal.read(fingerprint) ?: return ExistingResolution.Continue
        if (
            record.transactionId != transactionId ||
            record.threadId != threadId ||
            record.subscriptionId != subscriptionId
        ) {
            return ExistingResolution.Blocked("MMS_PROVIDER_CORRELATION_CONFLICT", false)
        }
        return when (record.phase) {
            IncomingMmsProviderJournal.Phase.READY -> {
                val id = record.providerMessageId
                    ?: return ExistingResolution.Blocked("MMS_PROVIDER_READY_ID_MISSING", false)
                when (rootPresence(id)) {
                    RootPresence.PRESENT -> ExistingResolution.Duplicate(id, record.contentState)
                    RootPresence.ABSENT -> {
                        if (journal.remove(fingerprint)) ExistingResolution.Continue
                        else ExistingResolution.Blocked("MMS_PROVIDER_STALE_JOURNAL", true)
                    }
                    RootPresence.ERROR -> ExistingResolution.Blocked("MMS_PROVIDER_LOOKUP_FAILED", false)
                }
            }
            IncomingMmsProviderJournal.Phase.ROOT_INSERTED -> {
                val id = record.providerMessageId
                    ?: return ExistingResolution.Blocked("MMS_PROVIDER_ROOT_ID_MISSING", false)
                val cleaned = cleanupRoot(id, null)
                val cleared = cleaned && journal.remove(fingerprint)
                if (cleared) ExistingResolution.Continue
                else ExistingResolution.Blocked("MMS_PROVIDER_RECOVERY_REQUIRED", cleaned)
            }
            IncomingMmsProviderJournal.Phase.BUILDING -> {
                when (
                    val lookup = lookupRoot(
                        transactionId = transactionId,
                        threadId = threadId,
                        subscriptionId = subscriptionId
                    )
                ) {
                    is RootLookup.Found -> {
                        val cleaned = cleanupRoot(lookup.id, lookup.uri)
                        val cleared = cleaned && journal.remove(fingerprint)
                        if (cleared) ExistingResolution.Continue
                        else ExistingResolution.Blocked("MMS_PROVIDER_RECOVERY_REQUIRED", cleaned)
                    }
                    RootLookup.Absent -> {
                        if (journal.remove(fingerprint)) ExistingResolution.Continue
                        else ExistingResolution.Blocked("MMS_PROVIDER_STALE_JOURNAL", true)
                    }
                    RootLookup.Error -> ExistingResolution.Blocked("MMS_PROVIDER_LOOKUP_FAILED", false)
                }
            }
        }
    }

    private fun lookupRoot(
        transactionId: String,
        threadId: Long,
        subscriptionId: Int
    ): RootLookup = runCatching {
        appContext.contentResolver.query(
            Telephony.Mms.CONTENT_URI,
            arrayOf(Telephony.Mms._ID),
            "${Telephony.Mms.TRANSACTION_ID}=? AND " +
                "${Telephony.Mms.MESSAGE_BOX}=? AND " +
                "${Telephony.Mms.THREAD_ID}=? AND " +
                "${Telephony.Mms.SUBSCRIPTION_ID}=? AND " +
                "${Telephony.Mms.CREATOR}=?",
            arrayOf(
                transactionId,
                Telephony.Mms.MESSAGE_BOX_INBOX.toString(),
                threadId.toString(),
                subscriptionId.toString(),
                appContext.packageName
            ),
            null
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(Telephony.Mms._ID)
            var match: Long? = null
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex).takeIf { it > 0L } ?: continue
                if (match != null && match != id) return@use RootLookup.Error
                match = id
            }
            match?.let {
                RootLookup.Found(it, ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, it))
            } ?: RootLookup.Absent
        } ?: RootLookup.Error
    }.getOrDefault(RootLookup.Error)

    private fun cleanupRoot(providerMessageId: Long, knownUri: Uri?): Boolean {
        val uri = knownUri ?: ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, providerMessageId)
        val deleted = runCatching { appContext.contentResolver.delete(uri, null, null) }.getOrNull()
            ?: return false
        if (deleted > 0) return true
        return rootPresence(providerMessageId) == RootPresence.ABSENT
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

    private fun rootPresence(providerMessageId: Long): RootPresence {
        if (providerMessageId <= 0L) return RootPresence.ERROR
        val uri = ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, providerMessageId)
        return runCatching {
            appContext.contentResolver.query(
                uri,
                arrayOf(Telephony.Mms._ID),
                null,
                null,
                null
            )?.use { if (it.moveToFirst()) RootPresence.PRESENT else RootPresence.ABSENT }
                ?: RootPresence.ERROR
        }.getOrDefault(RootPresence.ERROR)
    }

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

    private companion object {
        const val MESSAGE_TYPE_RETRIEVE_CONF = 0x84
        const val MMS_VERSION_1_2 = 0x12
        const val ADDRESS_TYPE_FROM = 0x89
        const val UTF_8_MIB_ENUM = 106
        const val ROOT_CONTENT_TYPE = "application/vnd.wap.multipart.mixed"
    }
}
