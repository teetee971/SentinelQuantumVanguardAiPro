package com.sentinel.quantum.security

import android.content.Context
import org.json.JSONObject
import java.security.MessageDigest

/** Durable recovery + bounded idempotence journal for incoming MMS provider projections. */
internal class IncomingMmsProviderJournal(context: Context) {
    enum class Phase { BUILDING, ROOT_INSERTED, READY }
    enum class ContentState { SAFE, QUARANTINED }

    data class Record(
        val correlationKey: String,
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
        correlationKey: String,
        transactionId: String,
        threadId: Long,
        subscriptionId: Int,
        contentState: ContentState,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean = synchronized(LOCK) {
        if (
            !validFingerprint(correlationKey) ||
            !validTransactionId(transactionId) ||
            threadId <= 0L ||
            subscriptionId < 0 ||
            nowMs < 0L
        ) return@synchronized false

        pruneReadyLocked(nowMs)
        if (preferences.contains(key(correlationKey))) return@synchronized false
        makeRoomForNewRecordLocked()
        val count = preferences.all.keys.count { it.startsWith(KEY_PREFIX) }
        if (count >= MAX_RECORDS) return@synchronized false
        writeLocked(
            Record(
                correlationKey = correlationKey,
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
        correlationKey: String,
        providerMessageId: Long,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean = transition(correlationKey, providerMessageId, Phase.ROOT_INSERTED, nowMs)

    fun markReady(
        correlationKey: String,
        providerMessageId: Long,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean = transition(correlationKey, providerMessageId, Phase.READY, nowMs)

    fun read(correlationKey: String): Record? = synchronized(LOCK) {
        if (!validFingerprint(correlationKey)) return@synchronized null
        decode(correlationKey, preferences.getString(key(correlationKey), null))
    }

    fun all(): List<Record> = synchronized(LOCK) { allLocked() }

    fun remove(correlationKey: String): Boolean = synchronized(LOCK) {
        validFingerprint(correlationKey) && preferences.edit().remove(key(correlationKey)).commit()
    }

    fun pruneExpiredReady(nowMs: Long = System.currentTimeMillis()): Int = synchronized(LOCK) {
        if (nowMs < 0L) return@synchronized 0
        pruneReadyLocked(nowMs)
    }

    private fun transition(
        correlationKey: String,
        providerMessageId: Long,
        phase: Phase,
        nowMs: Long
    ): Boolean = synchronized(LOCK) {
        if (!validFingerprint(correlationKey) || providerMessageId <= 0L || nowMs < 0L) {
            return@synchronized false
        }
        val current = decode(correlationKey, preferences.getString(key(correlationKey), null))
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
        val expired = allLocked().filter { it.phase == Phase.READY && it.updatedAtMs < cutoff }
        if (expired.isEmpty()) return 0
        val editor = preferences.edit()
        expired.forEach { editor.remove(key(it.correlationKey)) }
        return if (editor.commit()) expired.size else 0
    }

    /** Never evict an incomplete recovery record merely to admit a new message. */
    private fun makeRoomForNewRecordLocked() {
        val records = allLocked()
        if (records.size < MAX_RECORDS) return
        val ready = records.filter { it.phase == Phase.READY }.sortedBy { it.updatedAtMs }
        if (ready.isEmpty()) return
        val toRemove = (records.size - MAX_RECORDS + 1).coerceAtLeast(1)
        val editor = preferences.edit()
        ready.take(toRemove).forEach { editor.remove(key(it.correlationKey)) }
        editor.commit()
    }

    private fun allLocked(): List<Record> = preferences.all.asSequence()
        .filter { (name, value) -> name.startsWith(KEY_PREFIX) && value is String }
        .mapNotNull { (name, value) ->
            val correlationKey = name.removePrefix(KEY_PREFIX)
            decode(correlationKey, value as String)
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
        return preferences.edit().putString(key(record.correlationKey), encoded).commit()
    }

    private fun decode(correlationKey: String, encoded: String?): Record? {
        if (!validFingerprint(correlationKey) || encoded.isNullOrBlank() || encoded.length > MAX_ENCODED_CHARS) {
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
                correlationKey,
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

    private fun key(correlationKey: String) = KEY_PREFIX + correlationKey

    companion object {
        private const val PREFS_NAME = "sentinel_incoming_mms_provider_journal_v3"
        private const val KEY_PREFIX = "record."
        private const val SCHEMA_VERSION = 3
        private const val MAX_ENCODED_CHARS = 896
        private const val MAX_RECORDS = 2048
        internal const val READY_RETENTION_MS = 30L * 24L * 60L * 60L * 1000L
        private val FINGERPRINT = Regex("^[0-9a-f]{64}$")
        private val TRANSACTION_ID = Regex("^[\\x21-\\x7E]{1,128}$")
        private val LOCK = Any()

        internal fun validFingerprint(value: String): Boolean = FINGERPRINT.matches(value)
        internal fun validTransactionId(value: String): Boolean = TRANSACTION_ID.matches(value)

        internal fun correlationKey(
            contentFingerprint: String,
            transactionId: String,
            threadId: Long,
            subscriptionId: Int
        ): String? {
            if (
                !validFingerprint(contentFingerprint) ||
                !validTransactionId(transactionId) ||
                threadId <= 0L ||
                subscriptionId < 0
            ) return null
            val material = "$contentFingerprint\n$transactionId\n$threadId\n$subscriptionId"
            return MessageDigest.getInstance("SHA-256")
                .digest(material.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        }
    }
}
