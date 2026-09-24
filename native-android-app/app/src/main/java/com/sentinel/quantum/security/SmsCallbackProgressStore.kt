package com.sentinel.quantum.security

import android.content.Context

/**
 * App-private, bounded persistence for multipart SMS callback progress.
 * Only opaque callback metadata is stored; no destination or message body is persisted here.
 *
 * Terminal entries are retained as tombstones until TTL expiry so a late or duplicated Android
 * callback cannot recreate progress after a send has already failed or delivery has completed.
 */
class SmsCallbackProgressStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun record(
        sendToken: Int,
        providerMessageId: Long,
        partIndex: Int,
        partCount: Int,
        stage: SmsDeliveryStatusBus.Stage,
        successful: Boolean,
        nowMs: Long = System.currentTimeMillis()
    ): SmsCallbackProgress.Outcome? = synchronized(LOCK) {
        if (sendToken <= 0 || providerMessageId <= 0L) return@synchronized null
        prune(nowMs)

        val key = key(sendToken, providerMessageId)
        val raw = preferences.getString(key, null)
        val existing = decode(raw, nowMs)
        if (existing?.terminal == true) return@synchronized null

        val outcome = SmsCallbackProgress.record(
            current = existing?.state,
            partIndex = partIndex,
            partCount = partCount,
            stage = stage,
            successful = successful
        ) ?: return@synchronized null

        val createdAtMs = existing?.createdAtMs ?: nowMs
        val persisted = Persisted(
            createdAtMs = createdAtMs,
            terminal = outcome.terminal,
            state = outcome.state
        )
        if (!preferences.edit().putString(key, encode(persisted)).commit()) {
            return@synchronized null
        }
        trimToBound(nowMs)
        outcome
    }

    private fun prune(nowMs: Long) {
        val expired = preferences.all.mapNotNull { (key, value) ->
            val raw = value as? String ?: return@mapNotNull key
            val persisted = decode(raw, nowMs, enforceTtl = false) ?: return@mapNotNull key
            if (nowMs - persisted.createdAtMs > TTL_MS) key else null
        }
        if (expired.isNotEmpty()) {
            val editor = preferences.edit()
            expired.forEach(editor::remove)
            editor.commit()
        }
    }

    private fun trimToBound(nowMs: Long) {
        val entries = preferences.all.mapNotNull { (key, value) ->
            val persisted = decode(value as? String, nowMs, enforceTtl = false)
                ?: return@mapNotNull null
            Triple(key, persisted.createdAtMs, persisted.terminal)
        }
        val overflow = entries.size - MAX_TRACKED
        if (overflow <= 0) return

        // Preserve terminal tombstones preferentially: they suppress late/duplicate Android
        // callbacks for already completed sends. Under pressure, discard the oldest in-flight
        // progress first; only evict a tombstone when terminal entries alone exceed the bound.
        val evictionOrder = entries.sortedWith(
            compareBy<Triple<String, Long, Boolean>> { it.third }
                .thenBy { it.second }
        )
        val editor = preferences.edit()
        evictionOrder.take(overflow).forEach { editor.remove(it.first) }
        editor.commit()
    }

    private data class Persisted(
        val createdAtMs: Long,
        val terminal: Boolean,
        val state: SmsCallbackProgress.State
    )

    private fun encode(persisted: Persisted): String = listOf(
        persisted.createdAtMs.toString(),
        if (persisted.terminal) "1" else "0",
        persisted.state.partCount.toString(),
        persisted.state.sentOk.sorted().joinToString(","),
        persisted.state.sentFailed.sorted().joinToString(","),
        persisted.state.deliveredOk.sorted().joinToString(","),
        persisted.state.deliveryFailed.sorted().joinToString(",")
    ).joinToString("|")

    private fun decode(
        raw: String?,
        nowMs: Long,
        enforceTtl: Boolean = true
    ): Persisted? {
        if (raw.isNullOrBlank()) return null
        val parts = raw.split("|", limit = 7)
        if (parts.size != 7) return null
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

        return Persisted(
            createdAtMs = created,
            terminal = terminal,
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
