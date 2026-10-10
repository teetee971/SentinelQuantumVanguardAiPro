package com.sentinel.quantum.security

import android.content.Context
import android.content.SharedPreferences

/**
 * App-private, bounded persistence for multipart SMS callback progress.
 * Only opaque callback metadata is stored; no destination or message body is persisted here.
 *
 * Terminal entries are retained as tombstones until TTL expiry so a late or duplicated Android
 * callback cannot recreate progress after a send has already failed or delivery has completed.
 */
class SmsCallbackProgressStore internal constructor(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE))

    fun record(
        sendToken: Int,
        providerMessageId: Long,
        partIndex: Int,
        partCount: Int,
        stage: SmsDeliveryStatusBus.Stage,
        successful: Boolean,
        nowMs: Long = System.currentTimeMillis(),
        onPersistenceFailure: () -> Unit = {}
    ): SmsCallbackProgress.Outcome? = synchronized(LOCK) {
        if (sendToken <= 0 || providerMessageId <= 0L) return@synchronized null
        if (!prune(nowMs)) {
            onPersistenceFailure()
        }

        val key = key(sendToken, providerMessageId)
        val rawExists = preferences.contains(key)
        val raw = runCatching { preferences.getString(key, null) }.getOrElse {
            onPersistenceFailure()
            return@synchronized null
        }
        val existing = decode(raw, nowMs)
        if (rawExists && existing == null) {
            // A present but malformed/expired record is not an empty ledger entry. Replacing it
            // would erase the only durable indication that a callback was already observed and
            // could let the watchdog later manufacture a timeout. Leave repair to recovery.
            onPersistenceFailure()
            return@synchronized null
        }
        if (existing?.terminal == true && existing.providerApplied) return@synchronized null
        if (existing != null && existing.state.partCount != partCount) return@synchronized null

        val outcome = if (existing?.terminal == true) {
            SmsCallbackProgress.pendingProviderOutcome(existing.state)
        } else SmsCallbackProgress.record(
            current = existing?.state,
            partIndex = partIndex,
            partCount = partCount,
            stage = stage,
            successful = successful
        ) ?: existing?.takeIf { !it.providerApplied && partIndex in 0 until partCount }
            ?.let { SmsCallbackProgress.pendingProviderOutcome(it.state) }
            ?: return@synchronized null

        val createdAtMs = existing?.createdAtMs ?: nowMs
        val persisted = Persisted(
            createdAtMs = createdAtMs,
            terminal = outcome.terminal,
            state = outcome.state
        )
        if (!preferences.edit().putString(key, encode(persisted)).commit()) {
            onPersistenceFailure()
        }
        // The radio transition remains usable even when cleanup/storage fails.
        // The receiver must report that failure and suppress certification proofs.
        val trimmed = trimToBound(nowMs, protectedKey = key)
        if (!trimmed) {
            onPersistenceFailure()
            // If corruption has already pushed the store beyond its physical bound, there may be
            // too few valid rows to evict. A brand-new callback must never make that degraded store
            // grow further. Roll back only the new ledger row; never erase an existing callback
            // history entry. The radio outcome is still returned, but persistence stays degraded.
            if (!rawExists && !rollbackNewEntryIfStillOverCapacity(key)) {
                onPersistenceFailure()
            }
        }
        outcome
    }

    data class PendingProviderWrite(val sendToken: Int, val providerMessageId: Long,
                                    val outcome: SmsCallbackProgress.Outcome)

    fun pendingProviderWrites(nowMs: Long = System.currentTimeMillis()): List<PendingProviderWrite> = synchronized(LOCK) {
        preferences.all.entries.mapNotNull { (key, value) ->
            // A valid but expired record is ordinary retention cleanup. Any other malformed
            // record is different: silently dropping it would let the submission watchdog
            // convert an observed callback into a fabricated timeout.
            val record = decode(value as? String, nowMs, enforceTtl = false)
                ?: throw IllegalStateException("SMS callback progress contains corrupt state")
            val ids = key.split(":", limit = 2)
            if (ids.size != 2) {
                throw IllegalStateException("SMS callback progress key is corrupt")
            }
            val sendToken = ids[0].toIntOrNull()?.takeIf { it > 0 }
                ?: throw IllegalStateException("SMS callback progress token is corrupt")
            val providerId = ids[1].toLongOrNull()?.takeIf { it > 0L }
                ?: throw IllegalStateException("SMS callback progress provider id is corrupt")
            if (nowMs - record.createdAtMs > TTL_MS) return@mapNotNull null
            if (record.providerApplied) return@mapNotNull null
            PendingProviderWrite(sendToken, providerId, SmsCallbackProgress.pendingProviderOutcome(record.state))
        }.take(MAX_TRACKED)
    }

    fun markProviderApplied(sendToken: Int, providerMessageId: Long, state: SmsCallbackProgress.State,
                            nowMs: Long = System.currentTimeMillis()): Boolean = synchronized(LOCK) {
        val storageKey = key(sendToken, providerMessageId)
        val current = decode(preferences.getString(storageKey, null), nowMs) ?: return@synchronized false
        if (current.state != state) return@synchronized false
        preferences.edit().putString(storageKey, encode(current.copy(providerApplied = true))).commit()
    }

    private fun prune(nowMs: Long): Boolean {
        var storageHealthy = true
        val expired = mutableListOf<String>()
        preferences.all.forEach { (key, value) ->
            val raw = value as? String
            if (raw == null) {
                storageHealthy = false
                return@forEach
            }
            val persisted = decode(raw, nowMs, enforceTtl = false)
            if (persisted == null) {
                // Corruption is recovery evidence, not ordinary retention garbage. Preserve it so
                // the current callback cannot recreate a fresh ledger entry over unknown history.
                storageHealthy = false
                return@forEach
            }
            if (nowMs - persisted.createdAtMs > TTL_MS) {
                expired += key
            }
        }
        if (expired.isNotEmpty()) {
            val editor = preferences.edit()
            expired.forEach(editor::remove)
            if (!editor.commit()) storageHealthy = false
        }
        return storageHealthy
    }

    private fun trimToBound(nowMs: Long, protectedKey: String): Boolean {
        val allEntries = preferences.all
        val overflow = allEntries.size - MAX_TRACKED
        if (overflow <= 0) return true

        // Corrupt records still consume real SharedPreferences capacity even though they cannot be
        // decoded. Never erase them as ordinary retention garbage; instead evict only the oldest
        // valid records. If there are not enough valid records to restore the bound, fail closed
        // and leave the corruption visible to recovery rather than pretending the store is healthy.
        val removable = allEntries.mapNotNull { (key, value) ->
            if (key == protectedKey) return@mapNotNull null
            val persisted = decode(value as? String, nowMs, enforceTtl = false)
                ?: return@mapNotNull null
            key to persisted.createdAtMs
        }.sortedBy { it.second }
        if (removable.size < overflow) return false

        val editor = preferences.edit()
        removable.take(overflow).forEach { editor.remove(it.first) }
        return editor.commit()
    }

    private fun rollbackNewEntryIfStillOverCapacity(key: String): Boolean {
        val size = runCatching { preferences.all.size }.getOrElse { return false }
        if (size <= MAX_TRACKED) return true
        return runCatching { preferences.edit().remove(key).commit() }.getOrDefault(false)
    }

    private data class Persisted(
        val createdAtMs: Long,
        val terminal: Boolean,
        val state: SmsCallbackProgress.State,
        val providerApplied: Boolean = false
    )

    private fun encode(persisted: Persisted): String = listOf(
        persisted.createdAtMs.toString(),
        if (persisted.terminal) "1" else "0",
        persisted.state.partCount.toString(),
        persisted.state.sentOk.sorted().joinToString(","),
        persisted.state.sentFailed.sorted().joinToString(","),
        persisted.state.deliveredOk.sorted().joinToString(","),
        persisted.state.deliveryFailed.sorted().joinToString(","),
        if (persisted.providerApplied) "1" else "0"
    ).joinToString("|")

    private fun decode(
        raw: String?,
        nowMs: Long,
        enforceTtl: Boolean = true
    ): Persisted? {
        if (raw.isNullOrBlank()) return null
        val parts = raw.split("|", limit = 8)
        if (parts.size !in 7..8) return null
        val created = parts[0].toLongOrNull() ?: return null
        if (created <= 0L || created > nowMs + MAX_CLOCK_SKEW_MS) return null
        if (enforceTtl && nowMs - created > TTL_MS) return null
        val terminal = when (parts[1]) {
            "0" -> false
            "1" -> true
            else -> return null
        }
        val partCount = parts[2].toIntOrNull()
            ?.takeIf { it in 1..SmsCallbackProgress.MAX_PARTS }
            ?: return null

        fun parseIndexes(value: String): Set<Int>? {
            if (value.isBlank()) return emptySet()
            val indexes = value.split(",").map { it.toIntOrNull() ?: return null }.toSet()
            return indexes.takeIf { set -> set.all { it in 0 until partCount } }
        }

        val sentOk = parseIndexes(parts[3]) ?: return null
        val sentFailed = parseIndexes(parts[4]) ?: return null
        val deliveredOk = parseIndexes(parts[5]) ?: return null
        val deliveryFailed = parseIndexes(parts[6]) ?: return null
        if (sentOk.intersect(sentFailed).isNotEmpty()) return null
        if (deliveredOk.intersect(deliveryFailed).isNotEmpty()) return null
        val providerApplied = when (parts.getOrNull(7)) {
            null, "0" -> false
            "1" -> true
            else -> return null
        }

        return Persisted(
            createdAtMs = created,
            terminal = terminal,
            providerApplied = providerApplied,
            state = SmsCallbackProgress.State(
                partCount = partCount,
                sentOk = sentOk,
                sentFailed = sentFailed,
                deliveredOk = deliveredOk,
                deliveryFailed = deliveryFailed
            )
        )
    }

    private fun key(sendToken: Int, providerMessageId: Long) = "$sendToken:$providerMessageId"

    private companion object {
        const val PREFERENCES = "sentinel_sms_callback_progress_v2"
        const val MAX_TRACKED = 128
        const val TTL_MS = 24L * 60L * 60L * 1000L
        const val MAX_CLOCK_SKEW_MS = 5L * 60L * 1000L
        val LOCK = Any()
    }
}
