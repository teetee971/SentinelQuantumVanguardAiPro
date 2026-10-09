package com.sentinel.quantum.security

import android.content.Context
import org.json.JSONObject

/**
 * Small durable journal for outgoing MMS provider mutations.
 *
 * It stores only correlation/state metadata: never destination, message text, or attachment data.
 * Synchronous commit() is deliberate because provider mutation must never outrun its recovery marker.
 */
internal class MmsProviderJournal(context: Context) {
    enum class Phase {
        BUILDING,
        ROOT_INSERTED,
        READY,
        SUBMITTED,
        SUBMISSION_UNKNOWN,
        RESULT_SENT,
        RESULT_FAILED
    }

    data class Record(
        val token: String,
        val transactionId: String,
        val providerMessageId: Long?,
        val phase: Phase,
        val updatedAtMs: Long
    )

    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun begin(token: String, transactionId: String, nowMs: Long = System.currentTimeMillis()): Boolean =
        withJournalLock {
            if (!validToken(token) || !validTransactionId(transactionId) || nowMs < 0L) return@withJournalLock false
            val recordKey = key(token)
            if (!preferences.contains(recordKey)) {
                val persistedCount = preferences.all.keys.count { it.startsWith(KEY_PREFIX) }
                if (persistedCount >= MAX_RECORDS) return@withJournalLock false
            }
            write(Record(token, transactionId, null, Phase.BUILDING, nowMs))
        }

    fun recordRoot(token: String, providerMessageId: Long, nowMs: Long = System.currentTimeMillis()): Boolean =
        withJournalLock { transition(token, providerMessageId, Phase.ROOT_INSERTED, nowMs) }

    fun markReady(token: String, providerMessageId: Long, nowMs: Long = System.currentTimeMillis()): Boolean =
        withJournalLock { transition(token, providerMessageId, Phase.READY, nowMs) }

    fun markSubmitted(token: String, providerMessageId: Long, nowMs: Long = System.currentTimeMillis()): Boolean =
        withJournalLock { transition(token, providerMessageId, Phase.SUBMITTED, nowMs) }

    fun markSubmissionUnknown(token: String, providerMessageId: Long, nowMs: Long = System.currentTimeMillis()): Boolean =
        withJournalLock { transition(token, providerMessageId, Phase.SUBMISSION_UNKNOWN, nowMs) }

    fun markResult(
        token: String,
        providerMessageId: Long,
        successful: Boolean,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean = withJournalLock {
        if (!validToken(token) || providerMessageId <= 0L || nowMs < 0L) return@withJournalLock false
        val current = read(token) ?: return@withJournalLock false
        if (current.providerMessageId != null && current.providerMessageId != providerMessageId) return@withJournalLock false
        if (current.phase == Phase.RESULT_SENT || current.phase == Phase.RESULT_FAILED) return@withJournalLock false
        write(current.copy(
            providerMessageId = providerMessageId,
            phase = if (successful) Phase.RESULT_SENT else Phase.RESULT_FAILED,
            updatedAtMs = nowMs
        ))
    }

    fun staleTransportSubmissions(nowMs: Long = System.currentTimeMillis()): List<Record> = withJournalLock {
        if (nowMs < 0L) return@withJournalLock emptyList()
        all().filter { record ->
            record.providerMessageId != null &&
                record.phase in setOf(Phase.READY, Phase.SUBMITTED, Phase.SUBMISSION_UNKNOWN) &&
                nowMs >= record.updatedAtMs &&
                nowMs - record.updatedAtMs >= MMS_CALLBACK_TIMEOUT_MS
        }
    }

    /**
     * A process restart makes a READY record ambiguous: Android may have been invoked immediately
     * before process death, or the process may have died just before invocation. Preserve the row
     * and record uncertainty rather than deleting it or inventing a transport result.
     */
    fun reconcileReadyAfterProcessDeath(nowMs: Long = System.currentTimeMillis()): Int = withJournalLock {
        if (nowMs < 0L) return@withJournalLock 0
        var changed = 0
        all().forEach { record ->
            val id = record.providerMessageId
            if (record.phase == Phase.READY && id != null &&
                markSubmissionUnknown(record.token, id, nowMs)) {
                changed++
            }
        }
        changed
    }

    fun remove(token: String): Boolean = withJournalLock {
        validToken(token) && preferences.edit().remove(key(token)).commit()
    }

    fun read(token: String): Record? = withJournalLock {
        if (validToken(token)) decode(
            token,
            preferences.getString(key(token), null)
        ) else null
    }

    fun all(): List<Record> = withJournalLock {
        preferences.all.asSequence()
            .filter { (name, value) -> name.startsWith(KEY_PREFIX) && value is String }
            .mapNotNull { (name, value) ->
                val token = name.removePrefix(KEY_PREFIX)
                decode(token, value as String)
            }
            .sortedBy { it.updatedAtMs }
            .take(MAX_RECORDS)
            .toList()
    }

    private fun transition(token: String, providerMessageId: Long, phase: Phase, nowMs: Long): Boolean {
        if (!validToken(token) || providerMessageId <= 0L || nowMs < 0L) return false
        val current = read(token) ?: return false
        if (current.providerMessageId != null && current.providerMessageId != providerMessageId) return false
        if (current.phase == Phase.RESULT_SENT || current.phase == Phase.RESULT_FAILED) return false
        return write(current.copy(providerMessageId = providerMessageId, phase = phase, updatedAtMs = nowMs))
    }

    private fun write(record: Record): Boolean {
        val encoded = JSONObject()
            .put("schema", SCHEMA_VERSION)
            .put("transaction", record.transactionId)
            .put("provider_id", record.providerMessageId ?: JSONObject.NULL)
            .put("phase", record.phase.name)
            .put("updated_at_ms", record.updatedAtMs)
            .toString()
        return preferences.edit().putString(key(record.token), encoded).commit()
    }

    private fun decode(token: String, encoded: String?): Record? {
        if (!validToken(token) || encoded.isNullOrBlank() || encoded.length > MAX_ENCODED_CHARS) return null
        return runCatching {
            val json = JSONObject(encoded)
            if (json.optInt("schema", -1) != SCHEMA_VERSION) return@runCatching null
            val transactionId = json.getString("transaction")
            if (!validTransactionId(transactionId)) return@runCatching null
            val providerId = if (json.isNull("provider_id")) null else json.getLong("provider_id")
            if (providerId != null && providerId <= 0L) return@runCatching null
            val phase = Phase.valueOf(json.getString("phase"))
            val updatedAt = json.getLong("updated_at_ms")
            if (updatedAt < 0L) return@runCatching null
            Record(token, transactionId, providerId, phase, updatedAt)
        }.getOrNull()
    }

    private fun key(token: String) = KEY_PREFIX + token

    private inline fun <T> withJournalLock(block: () -> T): T = synchronized(LOCK) { block() }

    companion object {
        private const val PREFS_NAME = "sentinel_mms_provider_journal_v1"
        private const val KEY_PREFIX = "record."
        private const val SCHEMA_VERSION = 1
        private const val MAX_ENCODED_CHARS = 1024
        private const val MAX_RECORDS = 256
        const val MMS_CALLBACK_TIMEOUT_MS = 15L * 60L * 1000L
        private val LOCK = Any()
        private val TOKEN = Regex(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
        )

        internal fun validToken(value: String): Boolean = TOKEN.matches(value)
        internal fun validTransactionId(value: String): Boolean =
            value.isNotBlank() && value.length <= 40 && value.all { it.code in 0x21..0x7e }
    }
}
