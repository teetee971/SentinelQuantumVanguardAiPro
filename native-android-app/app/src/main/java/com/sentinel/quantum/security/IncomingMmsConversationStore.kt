package com.sentinel.quantum.security

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.Telephony

/**
 * Fail-closed projection of one validated incoming MMS into Android's canonical MMS provider.
 *
 * Android exposes root/address/part rows as separate provider mutations, so construction is
 * journaled and compensated through [MmsProviderProjectionTransaction]. The private SHA-256 stays
 * app-private and is never written into protocol columns. Replay detection uses a retained READY
 * ledger plus the real Message-ID/Transaction-ID metadata carried by the M-Retrieve.conf.
 */
internal class IncomingMmsConversationStore(context: Context) {
    sealed interface ProjectResult {
        data class Ready(
            val providerMessageId: Long,
            val replay: Boolean
        ) : ProjectResult

        data class Rejected(
            val reason: String,
            val cleanupConfirmed: Boolean
        ) : ProjectResult
    }

    private val appContext = context.applicationContext
    private val journal = IncomingMmsProviderJournal(appContext)

    fun project(
        plan: IncomingMmsProjectionPlan.Plan,
        nowMs: Long = System.currentTimeMillis()
    ): ProjectResult = synchronized(PROJECTION_LOCK) {
        projectLocked(plan, nowMs)
    }

    fun repairJournal(): Int = synchronized(PROJECTION_LOCK) {
        if (!holdsSmsRole()) return@synchronized 0
        var repaired = 0
        for (record in journal.all()) {
            if (record.phase == IncomingMmsProviderJournal.Phase.READY) continue
            if (repairRecord(record)) repaired++
        }
        repaired
    }

    private fun projectLocked(
        plan: IncomingMmsProjectionPlan.Plan,
        nowMs: Long
    ): ProjectResult {
        if (!holdsSmsRole()) return ProjectResult.Rejected("SMS_ROLE_NOT_HELD", true)
        if (nowMs < 0L || !validPlanForJournal(plan)) {
            return ProjectResult.Rejected("MMS_PROVIDER_INPUT_INVALID", true)
        }

        val durable = journal.read(plan.digestHex)
        if (durable != null) {
            if (!sameIdentity(durable, plan)) {
                return ProjectResult.Rejected("MMS_PROVIDER_DIGEST_IDENTITY_CONFLICT", true)
            }
            if (durable.phase == IncomingMmsProviderJournal.Phase.READY) {
                val id = durable.providerMessageId
                    ?: return ProjectResult.Rejected("MMS_PROVIDER_READY_LEDGER_INVALID", false)
                return ProjectResult.Ready(id, replay = true)
            }
            if (!repairRecord(durable)) {
                return ProjectResult.Rejected("MMS_PROVIDER_RECOVERY_PENDING", false)
            }
        }

        when (val existing = lookupRoot(identity(plan))) {
            is RootLookup.Found -> {
                if (!journal.markExistingReady(plan, existing.id, nowMs)) {
                    return ProjectResult.Rejected("MMS_PROVIDER_REPLAY_LEDGER_UNAVAILABLE", true)
                }
                return ProjectResult.Ready(existing.id, replay = true)
            }
            RootLookup.Ambiguous ->
                return ProjectResult.Rejected("MMS_PROVIDER_EXISTING_ROOT_AMBIGUOUS", true)
            RootLookup.Error ->
                return ProjectResult.Rejected("MMS_PROVIDER_EXISTING_ROOT_LOOKUP_FAILED", true)
            RootLookup.Absent -> Unit
        }

        var insertedRootUri: Uri? = null
        var rootInsertCleanupFailed = false
        val operations = object : MmsProviderProjectionTransaction.Operations {
            override fun beginJournal(): Boolean = journal.begin(plan, nowMs)

            override fun insertRoot(): Long? {
                if (!holdsSmsRole()) return null
                val threadId = runCatching {
                    Telephony.Threads.getOrCreateThreadId(appContext, plan.sender)
                }.getOrNull()?.takeIf { it > 0L } ?: return null

                val values = ContentValues().apply {
                    put(Telephony.Mms.THREAD_ID, threadId)
                    put(Telephony.Mms.DATE, plan.dateSeconds)
                    put(Telephony.Mms.DATE_SENT, 0L)
                    put(Telephony.Mms.READ, 0)
                    put(Telephony.Mms.SEEN, 0)
                    put(Telephony.Mms.MESSAGE_TYPE, MESSAGE_TYPE_RETRIEVE_CONF)
                    put(Telephony.Mms.MMS_VERSION, plan.mmsVersion)
                    put(Telephony.Mms.CONTENT_TYPE, plan.contentType)
                    plan.messageId?.let { put(Telephony.Mms.MESSAGE_ID, it) }
                    plan.transactionId?.let { put(Telephony.Mms.TRANSACTION_ID, it) }
                    put(Telephony.Mms.MESSAGE_SIZE, plan.messageSizeBytes)
                    put(Telephony.Mms.TEXT_ONLY, if (plan.textOnly) 1 else 0)
                    put(Telephony.Mms.SUBSCRIPTION_ID, plan.subscriptionId)
                }
                val uri = runCatching {
                    appContext.contentResolver.insert(Telephony.Mms.Inbox.CONTENT_URI, values)
                }.getOrNull() ?: return null
                insertedRootUri = uri

                val id = runCatching { ContentUris.parseId(uri) }.getOrNull()?.takeIf { it > 0L }
                if (id == null) {
                    // A non-null URI proves that provider mutation may have happened. The exact URI
                    // is the only safe cleanup handle when no numeric provider id can be parsed.
                    rootInsertCleanupFailed = !cleanupExactUri(uri)
                    return null
                }
                return id
            }

            override fun recordRoot(providerMessageId: Long): Boolean =
                journal.recordRoot(plan.digestHex, providerMessageId)

            override fun insertAddress(providerMessageId: Long): Boolean {
                if (!holdsSmsRole()) return false
                val values = ContentValues().apply {
                    put(Telephony.Mms.Addr.ADDRESS, plan.sender)
                    put(Telephony.Mms.Addr.CHARSET, UTF_8_MIB_ENUM)
                    put(Telephony.Mms.Addr.TYPE, ADDRESS_TYPE_FROM)
                }
                return runCatching {
                    appContext.contentResolver.insert(addressUri(providerMessageId), values) != null
                }.getOrDefault(false)
            }

            override fun insertParts(providerMessageId: Long): Boolean {
                if (!holdsSmsRole()) return false
                var sequence = 0
                for (part in plan.parts) {
                    when (part) {
                        is IncomingMmsProjectionPlan.Part.Text -> {
                            val values = ContentValues().apply {
                                put(Telephony.Mms.Part.SEQ, sequence++)
                                put(Telephony.Mms.Part.CONTENT_TYPE, part.mimeType)
                                put(Telephony.Mms.Part.CHARSET, UTF_8_MIB_ENUM)
                                put(Telephony.Mms.Part.TEXT, part.text)
                            }
                            if (runCatching {
                                    appContext.contentResolver.insert(partsUri(providerMessageId), values)
                                }.getOrNull() == null
                            ) return false
                        }
                        is IncomingMmsProjectionPlan.Part.Binary -> {
                            val values = ContentValues().apply {
                                put(Telephony.Mms.Part.SEQ, sequence++)
                                put(Telephony.Mms.Part.CONTENT_TYPE, part.mimeType)
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
                    }
                }
                return sequence == plan.parts.size && sequence > 0
            }

            override fun markReady(providerMessageId: Long): Boolean =
                journal.markReady(plan.digestHex, providerMessageId)

            override fun deleteRoot(providerMessageId: Long): Boolean =
                cleanupRoot(providerMessageId, insertedRootUri, identity(plan))

            override fun clearJournal(): Boolean =
                !rootInsertCleanupFailed && journal.remove(plan.digestHex)
        }

        return when (val result = MmsProviderProjectionTransaction.execute(operations)) {
            is MmsProviderProjectionTransaction.Result.Ready ->
                ProjectResult.Ready(result.providerMessageId, replay = false)
            is MmsProviderProjectionTransaction.Result.Rejected ->
                ProjectResult.Rejected(result.reason, result.cleanupConfirmed)
        }
    }

    private fun repairRecord(record: IncomingMmsProviderJournal.Record): Boolean {
        if (!holdsSmsRole()) return false
        return when (record.phase) {
            IncomingMmsProviderJournal.Phase.BUILDING -> {
                // A process can die after the root insert but before recordRoot() and before the
                // FROM row is inserted. Recovery must therefore locate app-owned roots by durable
                // protocol identity without requiring an address row that may not exist yet.
                when (val lookup = lookupRoot(identity(record), requireAddress = false)) {
                    is RootLookup.Found -> {
                        if (lookup.ownedByApp) {
                            cleanupRoot(lookup.id, lookup.uri, identity(record)) &&
                                journal.remove(record.digestHex)
                        } else {
                            // BUILDING contains no provider id. If another canonical app already has
                            // this exact message, there is no app-owned root to delete. Clearing the
                            // incomplete marker is safe; a replay will recreate the READY ledger.
                            journal.remove(record.digestHex)
                        }
                    }
                    RootLookup.Absent -> journal.remove(record.digestHex)
                    RootLookup.Ambiguous, RootLookup.Error -> false
                }
            }
            IncomingMmsProviderJournal.Phase.ROOT_INSERTED -> {
                val id = record.providerMessageId ?: return false
                when (verifyRoot(id, identity(record), requireAddress = false)) {
                    RootVerification.Owned ->
                        cleanupRoot(id, null, identity(record)) && journal.remove(record.digestHex)
                    RootVerification.Absent -> journal.remove(record.digestHex)
                    RootVerification.ExternalExact,
                    RootVerification.Mismatch,
                    RootVerification.Error -> false
                }
            }
            IncomingMmsProviderJournal.Phase.READY -> true
        }
    }

    private data class RootIdentity(
        val sender: String,
        val messageId: String?,
        val transactionId: String?,
        val dateSeconds: Long,
        val subscriptionId: Int
    )

    private fun identity(plan: IncomingMmsProjectionPlan.Plan) = RootIdentity(
        sender = plan.sender,
        messageId = plan.messageId,
        transactionId = plan.transactionId,
        dateSeconds = plan.dateSeconds,
        subscriptionId = plan.subscriptionId
    )

    private fun identity(record: IncomingMmsProviderJournal.Record) = RootIdentity(
        sender = record.sender,
        messageId = record.messageId,
        transactionId = record.transactionId,
        dateSeconds = record.dateSeconds,
        subscriptionId = record.subscriptionId
    )

    private sealed interface RootLookup {
        data class Found(
            val id: Long,
            val uri: Uri,
            val ownedByApp: Boolean
        ) : RootLookup
        data object Absent : RootLookup
        data object Ambiguous : RootLookup
        data object Error : RootLookup
    }

    private enum class RootVerification {
        Owned,
        ExternalExact,
        Absent,
        Mismatch,
        Error
    }

    private fun lookupRoot(
        identity: RootIdentity,
        requireAddress: Boolean = true
    ): RootLookup = runCatching {
        val selection = ArrayList<String>().apply {
            add("${Telephony.Mms.MESSAGE_BOX}=?")
            add("${Telephony.Mms.SUBSCRIPTION_ID}=?")
            add("${Telephony.Mms.DATE}=?")
            if (identity.messageId != null) add("${Telephony.Mms.MESSAGE_ID}=?")
            if (identity.transactionId != null) add("${Telephony.Mms.TRANSACTION_ID}=?")
        }.joinToString(" AND ")
        val args = ArrayList<String>().apply {
            add(Telephony.Mms.MESSAGE_BOX_INBOX.toString())
            add(identity.subscriptionId.toString())
            add(identity.dateSeconds.toString())
            identity.messageId?.let { add(it) }
            identity.transactionId?.let { add(it) }
        }.toTypedArray()

        appContext.contentResolver.query(
            Telephony.Mms.CONTENT_URI,
            arrayOf(Telephony.Mms._ID, Telephony.Mms.CREATOR),
            selection,
            args,
            null
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(Telephony.Mms._ID)
            val creatorIndex = cursor.getColumnIndexOrThrow(Telephony.Mms.CREATOR)
            var found: RootLookup.Found? = null
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex).takeIf { it > 0L } ?: continue
                if (requireAddress && !addressMatches(id, identity.sender)) continue
                val candidate = RootLookup.Found(
                    id = id,
                    uri = ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, id),
                    ownedByApp = cursor.getString(creatorIndex) == appContext.packageName
                )
                if (found != null && found.id != candidate.id) return@use RootLookup.Ambiguous
                found = candidate
            }
            found ?: RootLookup.Absent
        } ?: RootLookup.Error
    }.getOrDefault(RootLookup.Error)

    /**
     * `requireAddress=false` is used only for rollback/recovery. A failure can happen immediately
     * after root insertion, before the FROM row exists, so requiring that row would make cleanup
     * impossible exactly when compensation is most important.
     */
    private fun verifyRoot(
        providerMessageId: Long,
        identity: RootIdentity,
        requireAddress: Boolean
    ): RootVerification {
        if (providerMessageId <= 0L) return RootVerification.Mismatch
        val uri = ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, providerMessageId)
        return runCatching {
            appContext.contentResolver.query(
                uri,
                arrayOf(
                    Telephony.Mms._ID,
                    Telephony.Mms.CREATOR,
                    Telephony.Mms.MESSAGE_BOX,
                    Telephony.Mms.SUBSCRIPTION_ID,
                    Telephony.Mms.DATE,
                    Telephony.Mms.MESSAGE_ID,
                    Telephony.Mms.TRANSACTION_ID
                ),
                null,
                null,
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use RootVerification.Absent
                val box = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Mms.MESSAGE_BOX))
                val sub = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Mms.SUBSCRIPTION_ID))
                val date = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Mms.DATE))
                val messageId = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Mms.MESSAGE_ID))
                val transactionId = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Mms.TRANSACTION_ID))
                if (
                    box != Telephony.Mms.MESSAGE_BOX_INBOX ||
                    sub != identity.subscriptionId ||
                    date != identity.dateSeconds ||
                    messageId != identity.messageId ||
                    transactionId != identity.transactionId
                ) return@use RootVerification.Mismatch
                if (requireAddress && !addressMatches(providerMessageId, identity.sender)) {
                    return@use RootVerification.Mismatch
                }
                val creator = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Mms.CREATOR))
                if (creator == appContext.packageName) RootVerification.Owned
                else RootVerification.ExternalExact
            } ?: RootVerification.Error
        }.getOrDefault(RootVerification.Error)
    }

    private fun addressMatches(providerMessageId: Long, expected: String): Boolean = runCatching {
        appContext.contentResolver.query(
            addressUri(providerMessageId),
            arrayOf(Telephony.Mms.Addr.ADDRESS, Telephony.Mms.Addr.TYPE),
            null,
            null,
            null
        )?.use { cursor ->
            val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Mms.Addr.ADDRESS)
            val typeIndex = cursor.getColumnIndexOrThrow(Telephony.Mms.Addr.TYPE)
            var matchCount = 0
            var conflictingFrom = false
            while (cursor.moveToNext()) {
                if (cursor.getInt(typeIndex) != ADDRESS_TYPE_FROM) continue
                val actual = cursor.getString(addressIndex).orEmpty()
                if (sameAddress(actual, expected)) matchCount++ else conflictingFrom = true
            }
            matchCount == 1 && !conflictingFrom
        } ?: false
    }.getOrDefault(false)

    private fun sameAddress(actual: String, expected: String): Boolean {
        val left = actual.substringBefore('/').trim()
        val right = expected.substringBefore('/').trim()
        if (left == right) return true
        val leftPhone = CallRuleEngine.normalizeNumber(left)
        val rightPhone = CallRuleEngine.normalizeNumber(right)
        if (leftPhone != null && rightPhone != null) return leftPhone == rightPhone
        return left.contains('@') && right.contains('@') && left.equals(right, ignoreCase = true)
    }

    private fun cleanupRoot(
        providerMessageId: Long,
        knownUri: Uri?,
        identity: RootIdentity
    ): Boolean {
        when (verifyRoot(providerMessageId, identity, requireAddress = false)) {
            RootVerification.Absent -> return true
            RootVerification.Owned -> Unit
            RootVerification.ExternalExact,
            RootVerification.Mismatch,
            RootVerification.Error -> return false
        }
        val uri = knownUri ?: ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, providerMessageId)
        val deleted = runCatching { appContext.contentResolver.delete(uri, null, null) }.getOrNull()
            ?: return false
        if (deleted > 0) return true
        return verifyRoot(providerMessageId, identity, requireAddress = false) == RootVerification.Absent
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

    private fun validPlanForJournal(plan: IncomingMmsProjectionPlan.Plan): Boolean =
        IncomingMmsProviderJournal.validDigest(plan.digestHex) &&
            IncomingMmsProviderJournal.validSender(plan.sender) &&
            IncomingMmsProviderJournal.validCorrelation(plan.messageId, plan.transactionId) &&
            plan.dateSeconds >= 0L &&
            plan.subscriptionId >= 0 &&
            plan.parts.isNotEmpty()

    private fun sameIdentity(
        record: IncomingMmsProviderJournal.Record,
        plan: IncomingMmsProjectionPlan.Plan
    ): Boolean =
        record.digestHex == plan.digestHex &&
            sameAddress(record.sender, plan.sender) &&
            record.messageId == plan.messageId &&
            record.transactionId == plan.transactionId &&
            record.dateSeconds == plan.dateSeconds &&
            record.subscriptionId == plan.subscriptionId

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
        val PROJECTION_LOCK = Any()
        const val MESSAGE_TYPE_RETRIEVE_CONF = 0x84
        const val ADDRESS_TYPE_FROM = 0x89
        const val UTF_8_MIB_ENUM = 106
    }
}
