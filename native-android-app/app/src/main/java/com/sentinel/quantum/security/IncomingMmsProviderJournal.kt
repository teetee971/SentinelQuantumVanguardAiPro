package com.sentinel.quantum.security

import android.content.Context
import org.json.JSONObject

/**
 * Durable recovery + bounded idempotence journal for incoming MMS provider projections.
 *
 * Only non-content correlation metadata is persisted: SHA-256 fingerprint, carrier Transaction-ID,
 * Android thread/subscription ids, provider row id, projection phase and SAFE/QUARANTINED state.
 * Sender, text and attachment bytes never enter this journal. Synchronous commit() is deliberate
 * because a provider mutation must not outrun its recovery marker.
 */
internal class IncomingMmsProviderJournal(context: Context) {
    enum class Phase {
        BUILDING,
        ROOT_INSERTED,
        READY
    }

    enum class ContentState {
        SAFE,
        QUARANTINED
    }

    data class Record(
        val fingerprint: String,
        val transactionId: String,
        val threadId: Long,
        val subscriptionId: Int,
        val providerMessageId: Long?,
        val phase: Phase,
        val contentState: ContentState,
        val updatedAtMs: Long
    )

    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun begin(
        fingerprint: String,
        transactionId: String,
        threadId: Long,
        subscriptionId: Int,
        contentState: ContentState,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean = synchronized(LOCK) {
        if (
            !validFingerprint(fingerprint) ||
            !validTransactionId(transactionId) ||
            threadId <= 0L ||
            subscriptionId < 0 ||
            nowMs < 0L
        ) return@synchronized false

        pruneReadyLocked(nowMs)
        val recordKey = key(fingerprint)
        if (preferences.contains(recordKey)) return@synchronized false
        makeRoomForNewRecordLocked()
        val count = preferences.all.keys.count { it.startsWith(KEY_PREFIX) }
        if (count >= MAX_RECORDS) return@synchronized false
        writeLocked(
            Record(
                fingerprint = fingerprint,
                transactionId = transactionId,
                threadId = threadId,
                subscriptionId = subscriptionId,
                providerMessageId = null,
                phase = Phase.BUILDING,
                contentState = contentState,
                updatedAtMs = nowMs
            )
        )
    }

    fun recordRoot(
        fingerprint: String,
        providerMessageId: Long,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean = transition(fingerprint, providerMessageId, Phase.ROOT_INSERTED, nowMs)

    fun markReady(
        fingerprint: String,
        providerMessageId: Long,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean = transition(fingerprint, providerMessageId, Phase.READY, nowMs)

    fun read(fingerprint: String): Record? = synchronized(LOCK) {
        if (!validFingerprint(fingerprint)) return@synchronized null
        decode(fingerprint, preferences.getString(key(fingerprint), null))
    }

    fun all(): List<Record> = synchronized(LOCK) {
        allLocked()
    }

    fun remove(fingerprint: String): Boolean = synchronized(LOCK) {
        validFingerprint(fingerprint) && preferences.edit().remove(key(fingerprint)).commit()
    }

    fun pruneExpiredReady(nowMs: Long = System.currentTimeMillis()): Int = synchronized(LOCK) {
        if (nowMs < 0L) return@synchronized 0
        pruneReadyLocked(nowMs)
    }

    private fun transition(
        fingerprint: String,
        providerMessageId: Long,
        phase: Phase,
        nowMs: Long
    ): Boolean = synchronized(LOCK) {
        if (!validFingerprint(fingerprint) || providerMessageId <= 0L || nowMs < 0L) {
            return@synchronized false
        }
        val current = decode(fingerprint, preferences.getString(key(fingerprint), null))
            ?: return@synchronized false
        if (current.providerMessageId != null && current.providerMessageId != providerMessageId) {
            return@synchronized false
        }
        writeLocked(
            current.copy(
                providerMessageId = providerMessageId,
                phase = phase,
                updatedAtMs = nowMs
            )
        )
    }

    private fun pruneReadyLocked(nowMs: Long): Int {
        val cutoff = (nowMs - READY_RETENTION_MS).coerceAtLeast(0L)
        val expired = allLocked()
            .filter { it.phase == Phase.READY && it.updatedAtMs < cutoff }
        if (expired.isEmpty()) return 0
        val editor = preferences.edit()
        expired.forEach { editor.remove(key(it.fingerprint)) }
        return if (editor.commit()) expired.size else 0
    }

    /**
     * Capacity pressure may shorten only the READY dedup history. Incomplete recovery records are
     * never evicted to make room because doing so would orphan a provider mutation.
     */
    private fun makeRoomForNewRecordLocked() {
        val records = allLocked()
        if (records.size < MAX_RECORDS) return
        val ready = records
            .filter { it.phase == Phase.READY }
            .sortedBy { it.updatedAtMs }
        if (ready.isEmpty()) return

        val toRemove = (records.size - MAX_RECORDS + 1).coerceAtLeast(1)
        val editor = preferences.edit()
        ready.take(toRemove).forEach { editor.remove(key(it.fingerprint)) }
        editor.commit()
    }

    private fun allLocked(): List<Record> = preferences.all.asSequence()
        .filter { (name, value) -> name.startsWith(KEY_PREFIX) && value is String }
        .mapNotNull { (name, value) ->
            val fingerprint = name.removePrefix(KEY_PREFIX)
            decode(fingerprint, value as String)
        }
        .sortedBy { it.updatedAtMs }
        .take(MAX_RECORDS)
        .toList()

    private fun writeLocked(record: Record): Boolean {
        val encoded = JSONObject()
            .put("schema", SCHEMA_VERSION)
            .put("transaction_id", record.transactionId)
            .put("thread_id", record.threadId)
            .put("subscription_id", record.subscriptionId)
            .put("provider_id", record.providerMessageId ?: JSONObject.NULL)
            .put("phase", record.phase.name)
            .put("content_state", record.contentState.name)
            .put("updated_at_ms", record.updatedAtMs)
            .toString()
        return preferences.edit().putString(key(record.fingerprint), encoded).commit()
    }

    private fun decode(fingerprint: String, encoded: String?): Record? {
        if (!validFingerprint(fingerprint) || encoded.isNullOrBlank() || encoded.length > MAX_ENCODED_CHARS) {
            return null
        }
        return runCatching {
            val json = JSONObject(encoded)
            if (json.optInt("schema", -1) != SCHEMA_VERSION) return@runCatching null
            val transactionId = json.getString("transaction_id")
            if (!validTransactionId(transactionId)) return@runCatching null
            val threadId = json.getLong("thread_id")
            val subscriptionId = json.getInt("subscription_id")
            if (threadId <= 0L || subscriptionId < 0) return@runCatching null
            val providerId = if (json.isNull("provider_id")) null else json.getLong("provider_id")
            if (providerId != null && providerId <= 0L) return@runCatching null
            val phase = Phase.valueOf(json.getString("phase"))
            val state = ContentState.valueOf(json.getString("content_state"))
            val updatedAt = json.getLong("updated_at_ms")
            if (updatedAt < 0L) return@runCatching null
            Record(
                fingerprint,
                transactionId,
                threadId,
                subscriptionId,
                providerId,
                phase,
                state,
                updatedAt
            )
        }.getOrNull()
    }

    private fun key(fingerprint: String) = KEY_PREFIX + fingerprint

    companion object {
        private const val PREFS_NAME = "sentinel_incoming_mms_provider_journal_v2"
        private const val KEY_PREFIX = "record."
        private const val SCHEMA_VERSION = 2
        private const val MAX_ENCODED_CHARS = 896
        private const val MAX_RECORDS = 2048
        internal const val READY_RETENTION_MS = 30L * 24L * 60L * 60L * 1000L
        private val FINGERPRINT = Regex("^[0-9a-f]{64}$")
        private val TRANSACTION_ID = Regex("^[\\x21-\\x7E]{1,128}$")
        private val LOCK = Any()

        internal fun validFingerprint(value: String): Boolean = FINGERPRINT.matches(value)
        internal fun validTransactionId(value: String): Boolean = TRANSACTION_ID.matches(value)
    }
}
