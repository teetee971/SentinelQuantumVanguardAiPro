package com.sentinel.quantum.security

import android.content.Context
import org.json.JSONObject

/**
 * Durable build journal plus bounded replay ledger for incoming MMS provider projection.
 *
 * READY records are intentionally retained: a duplicate carrier callback must not resurrect a
 * message that the user later deleted from the Android provider. Old READY records are pruned to a
 * bounded window, while incomplete records are never silently evicted because they may own a
 * provider root that still requires recovery.
 */
internal class IncomingMmsProviderJournal(context: Context) {
    enum class Phase {
        BUILDING,
        ROOT_INSERTED,
        READY
    }

    data class Record(
        val digestHex: String,
        val messageId: String?,
        val transactionId: String?,
        val subscriptionId: Int,
        val providerMessageId: Long?,
        val phase: Phase,
        val updatedAtMs: Long
    )

    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    @Synchronized
    fun begin(plan: IncomingMmsProjectionPlan.Plan, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (!validDigest(plan.digestHex) ||
            !validCorrelation(plan.messageId, plan.transactionId) ||
            plan.subscriptionId < 0 ||
            nowMs < 0L
        ) return false

        val existing = read(plan.digestHex)
        if (existing != null) {
            // Never overwrite a durable READY replay marker or an unresolved build.
            return false
        }
        if (!ensureCapacityForNewRecord()) return false
        return write(
            Record(
                digestHex = plan.digestHex,
                messageId = plan.messageId,
                transactionId = plan.transactionId,
                subscriptionId = plan.subscriptionId,
                providerMessageId = null,
                phase = Phase.BUILDING,
                updatedAtMs = nowMs
            )
        )
    }

    @Synchronized
    fun recordRoot(
        digestHex: String,
        providerMessageId: Long,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean = transition(digestHex, providerMessageId, Phase.ROOT_INSERTED, nowMs)

    @Synchronized
    fun markReady(
        digestHex: String,
        providerMessageId: Long,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean = transition(digestHex, providerMessageId, Phase.READY, nowMs)

    @Synchronized
    fun remove(digestHex: String): Boolean =
        validDigest(digestHex) && preferences.edit().remove(key(digestHex)).commit()

    @Synchronized
    fun read(digestHex: String): Record? = if (validDigest(digestHex)) {
        decode(digestHex, preferences.getString(key(digestHex), null))
    } else {
        null
    }

    @Synchronized
    fun all(): List<Record> = preferences.all.asSequence()
        .filter { (name, value) -> name.startsWith(KEY_PREFIX) && value is String }
        .mapNotNull { (name, value) ->
            decode(name.removePrefix(KEY_PREFIX), value as String)
        }
        .sortedBy { it.updatedAtMs }
        .take(MAX_RECORDS)
        .toList()

    private fun transition(
        digestHex: String,
        providerMessageId: Long,
        phase: Phase,
        nowMs: Long
    ): Boolean {
        if (!validDigest(digestHex) || providerMessageId <= 0L || nowMs < 0L) return false
        val current = read(digestHex) ?: return false
        if (current.phase == Phase.READY && phase != Phase.READY) return false
        if (current.providerMessageId != null && current.providerMessageId != providerMessageId) return false
        return write(
            current.copy(
                providerMessageId = providerMessageId,
                phase = phase,
                updatedAtMs = nowMs
            )
        )
    }

    private fun ensureCapacityForNewRecord(): Boolean {
        val records = all()
        if (records.size < MAX_RECORDS) return true
        val readyOldestFirst = records.filter { it.phase == Phase.READY }
        if (readyOldestFirst.isEmpty()) return false
        val removeCount = (records.size - TARGET_AFTER_PRUNE).coerceAtLeast(1)
        val victims = readyOldestFirst.take(removeCount)
        if (victims.isEmpty()) return false
        val editor = preferences.edit()
        victims.forEach { editor.remove(key(it.digestHex)) }
        if (!editor.commit()) return false
        return all().size < MAX_RECORDS
    }

    private fun write(record: Record): Boolean {
        if (!validRecord(record)) return false
        val encoded = JSONObject()
            .put("schema", SCHEMA_VERSION)
            .put("message_id", record.messageId ?: JSONObject.NULL)
            .put("transaction_id", record.transactionId ?: JSONObject.NULL)
            .put("subscription_id", record.subscriptionId)
            .put("provider_id", record.providerMessageId ?: JSONObject.NULL)
            .put("phase", record.phase.name)
            .put("updated_at_ms", record.updatedAtMs)
            .toString()
        if (encoded.length > MAX_ENCODED_CHARS) return false
        return preferences.edit().putString(key(record.digestHex), encoded).commit()
    }

    private fun decode(digestHex: String, encoded: String?): Record? {
        if (!validDigest(digestHex) || encoded.isNullOrBlank() || encoded.length > MAX_ENCODED_CHARS) {
            return null
        }
        return runCatching {
            val json = JSONObject(encoded)
            if (json.optInt("schema", -1) != SCHEMA_VERSION) return@runCatching null
            val messageId = nullableString(json, "message_id")
            val transactionId = nullableString(json, "transaction_id")
            val subscriptionId = json.getInt("subscription_id")
            val providerId = if (json.isNull("provider_id")) null else json.getLong("provider_id")
            val phase = Phase.valueOf(json.getString("phase"))
            val updatedAt = json.getLong("updated_at_ms")
            val record = Record(
                digestHex = digestHex,
                messageId = messageId,
                transactionId = transactionId,
                subscriptionId = subscriptionId,
                providerMessageId = providerId,
                phase = phase,
                updatedAtMs = updatedAt
            )
            record.takeIf(::validRecord)
        }.getOrNull()
    }

    private fun nullableString(json: JSONObject, key: String): String? =
        if (json.isNull(key)) null else json.getString(key)

    private fun validRecord(record: Record): Boolean =
        validDigest(record.digestHex) &&
            validCorrelation(record.messageId, record.transactionId) &&
            record.subscriptionId >= 0 &&
            (record.providerMessageId == null || record.providerMessageId > 0L) &&
            record.updatedAtMs >= 0L &&
            when (record.phase) {
                Phase.BUILDING -> record.providerMessageId == null
                Phase.ROOT_INSERTED, Phase.READY -> record.providerMessageId != null
            }

    private fun key(digestHex: String) = KEY_PREFIX + digestHex

    companion object {
        private const val PREFS_NAME = "sentinel_incoming_mms_provider_journal_v1"
        private const val KEY_PREFIX = "record."
        private const val SCHEMA_VERSION = 1
        private const val MAX_ENCODED_CHARS = 2048
        private const val MAX_RECORDS = 512
        private const val TARGET_AFTER_PRUNE = 384
        private val DIGEST = Regex("^[0-9a-f]{64}$")

        internal fun validDigest(value: String): Boolean = DIGEST.matches(value)

        internal fun validProtocolId(value: String?): Boolean =
            value == null || (
                value.isNotBlank() &&
                    value.length <= 256 &&
                    value.all { it.code in 0x21..0x7e }
                )

        internal fun validCorrelation(messageId: String?, transactionId: String?): Boolean =
            validProtocolId(messageId) &&
                validProtocolId(transactionId) &&
                (messageId != null || transactionId != null)
    }
}
