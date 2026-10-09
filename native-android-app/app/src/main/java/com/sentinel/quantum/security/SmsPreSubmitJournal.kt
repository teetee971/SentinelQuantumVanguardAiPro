package com.sentinel.quantum.security

import android.content.Context
import org.json.JSONObject

/**
 * Durable, PII-free boundary journal for outgoing SMS provider mutations.
 *
 * PREPARING and PROVIDER_READY prove that SmsManager has not been entered and are therefore safe
 * to compensate after process death once ROLE_SMS is available again. TRANSPORT_STARTED is
 * deliberately ambiguous: the process may die after persisting the marker but before or after the
 * platform call starts, so recovery must never manufacture a FAILED transport outcome from it.
 */
internal class SmsPreSubmitJournal(context: Context) {
    enum class Phase { PREPARING, PROVIDER_READY, TRANSPORT_STARTED }

    data class Record(
        val token: String,
        val subscriptionId: Int,
        val createdAtMs: Long,
        val providerMessageId: Long?,
        val phase: Phase
    )

    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun begin(token: String, subscriptionId: Int, createdAtMs: Long): Boolean = synchronized(LOCK) {
        if (!validToken(token) || subscriptionId < 0 || createdAtMs < 0L) return@synchronized false
        val storageKey = key(token)
        if (!preferences.contains(storageKey)) {
            val count = preferences.all.keys.count { it.startsWith(KEY_PREFIX) }
            if (count >= MAX_RECORDS) return@synchronized false
        }
        write(Record(token, subscriptionId, createdAtMs, null, Phase.PREPARING))
    }

    fun recordProvider(token: String, providerMessageId: Long): Boolean = synchronized(LOCK) {
        if (providerMessageId <= 0L) return@synchronized false
        val current = read(token) ?: return@synchronized false
        if (current.phase != Phase.PREPARING || current.providerMessageId != null) return@synchronized false
        write(current.copy(providerMessageId = providerMessageId, phase = Phase.PROVIDER_READY))
    }

    fun markTransportStarted(token: String, providerMessageId: Long): Boolean = synchronized(LOCK) {
        val current = read(token) ?: return@synchronized false
        if (
            current.phase != Phase.PROVIDER_READY ||
            current.providerMessageId != providerMessageId ||
            providerMessageId <= 0L
        ) return@synchronized false
        write(current.copy(phase = Phase.TRANSPORT_STARTED))
    }

    /**
     * Retires transport ambiguity only after another durable subsystem has recorded a conclusive
     * telephony callback for this provider row. No record is a successful no-op because the normal
     * synchronous success path removes its marker before callbacks arrive. Multiple matches are a
     * correlation invariant violation and therefore fail closed without deleting anything.
     */
    fun removeTransportStartedForProvider(providerMessageId: Long): Boolean = synchronized(LOCK) {
        if (providerMessageId <= 0L) return@synchronized false
        val matches = all().filter {
            it.phase == Phase.TRANSPORT_STARTED && it.providerMessageId == providerMessageId
        }
        if (matches.isEmpty()) return@synchronized true
        if (matches.size != 1) return@synchronized false
        preferences.edit().remove(key(matches.single().token)).commit()
    }

    fun remove(token: String): Boolean = synchronized(LOCK) {
        validToken(token) && preferences.edit().remove(key(token)).commit()
    }

    fun read(token: String): Record? = synchronized(LOCK) {
        if (!validToken(token)) null else decode(token, preferences.getString(key(token), null))
    }

    fun all(): List<Record> = synchronized(LOCK) {
        preferences.all.asSequence()
            .filter { (name, value) -> name.startsWith(KEY_PREFIX) && value is String }
            .mapNotNull { (name, value) ->
                decode(name.removePrefix(KEY_PREFIX), value as String)
            }
            .sortedBy { it.createdAtMs }
            .take(MAX_RECORDS)
            .toList()
    }

    private fun write(record: Record): Boolean {
        val encoded = JSONObject()
            .put("schema", SCHEMA_VERSION)
            .put("subscription_id", record.subscriptionId)
            .put("created_at_ms", record.createdAtMs)
            .put("provider_id", record.providerMessageId ?: JSONObject.NULL)
            .put("phase", record.phase.name)
            .toString()
        return preferences.edit().putString(key(record.token), encoded).commit()
    }

    private fun decode(token: String, encoded: String?): Record? {
        if (!validToken(token) || encoded.isNullOrBlank() || encoded.length > MAX_ENCODED_CHARS) return null
        return runCatching {
            val json = JSONObject(encoded)
            if (json.optInt("schema", -1) != SCHEMA_VERSION) return@runCatching null
            val subscriptionId = json.getInt("subscription_id")
            val createdAtMs = json.getLong("created_at_ms")
            if (subscriptionId < 0 || createdAtMs < 0L) return@runCatching null
            val providerId = if (json.isNull("provider_id")) null else json.getLong("provider_id")
            if (providerId != null && providerId <= 0L) return@runCatching null
            val phase = Phase.valueOf(json.getString("phase"))
            if (phase == Phase.PREPARING && providerId != null) return@runCatching null
            if (phase != Phase.PREPARING && providerId == null) return@runCatching null
            Record(token, subscriptionId, createdAtMs, providerId, phase)
        }.getOrNull()
    }

    private fun key(token: String) = KEY_PREFIX + token

    companion object {
        private const val PREFS_NAME = "sentinel_sms_pre_submit_journal_v1"
        private const val KEY_PREFIX = "record."
        private const val SCHEMA_VERSION = 1
        private const val MAX_RECORDS = 128
        private const val MAX_ENCODED_CHARS = 512
        private val TOKEN = Regex(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
        )
        private val LOCK = Any()

        internal fun validToken(value: String): Boolean = TOKEN.matches(value)
    }
}