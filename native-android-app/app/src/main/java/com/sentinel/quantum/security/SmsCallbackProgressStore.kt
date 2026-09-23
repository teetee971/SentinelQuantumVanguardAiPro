package com.sentinel.quantum.security

import android.content.Context

/**
 * App-private, bounded persistence for multipart SMS callback progress.
 * Only opaque callback metadata is stored; no destination or message body is persisted here.
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
        val existing = decode(preferences.getString(key, null), nowMs)
        val outcome = SmsCallbackProgress.record(
            current = existing?.state,
            partIndex = partIndex,
            partCount = partCount,
            stage = stage,
            successful = successful
        ) ?: return@synchronized null

        if (outcome.failed || outcome.allDelivered) {
            preferences.edit().remove(key).commit()
        } else {
            preferences.edit().putString(key, encode(Persisted(nowMs, outcome.state))).commit()
            trimToBound()
        }
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

    private fun trimToBound() {
        val entries = preferences.all.mapNotNull { (key, value) ->
            val persisted = decode(value as? String, System.currentTimeMillis(), enforceTtl = false)
                ?: return@mapNotNull null
            key to persisted.createdAtMs
        }.sortedBy { it.second }
        val overflow = entries.size - MAX_TRACKED
        if (overflow <= 0) return
        val editor = preferences.edit()
        entries.take(overflow).forEach { editor.remove(it.first) }
        editor.commit()
    }

    private data class Persisted(
        val createdAtMs: Long,
        val state: SmsCallbackProgress.State
    )

    private fun encode(persisted: Persisted): String = listOf(
        persisted.createdAtMs.toString(),
        persisted.state.partCount.toString(),
        persisted.state.sentOk.sorted().joinToString(","),
        persisted.state.deliveredOk.sorted().joinToString(","),
        if (persisted.state.failed) "1" else "0"
    ).joinToString("|")

    private fun decode(
        raw: String?,
        nowMs: Long,
        enforceTtl: Boolean = true
    ): Persisted? {
        if (raw.isNullOrBlank()) return null
        val parts = raw.split("|", limit = 5)
        if (parts.size != 5) return null
        val created = parts[0].toLongOrNull() ?: return null
        if (created <= 0L || created > nowMs + MAX_CLOCK_SKEW_MS) return null
        if (enforceTtl && nowMs - created > TTL_MS) return null
        val partCount = parts[1].toIntOrNull()?.takeIf { it in 1..SmsCallbackProgress.MAX_PARTS } ?: return null

        fun parseIndexes(value: String): Set<Int>? {
            if (value.isBlank()) return emptySet()
            val indexes = value.split(",").map { it.toIntOrNull() ?: return null }.toSet()
            return indexes.takeIf { set -> set.all { it in 0 until partCount } }
        }

        val sent = parseIndexes(parts[2]) ?: return null
        val delivered = parseIndexes(parts[3]) ?: return null
        val failed = when (parts[4]) {
            "0" -> false
            "1" -> true
            else -> return null
        }
        return Persisted(
            createdAtMs = created,
            state = SmsCallbackProgress.State(partCount, sent, delivered, failed)
        )
    }

    private fun key(sendToken: Int, providerMessageId: Long) = "$sendToken:$providerMessageId"

    private companion object {
        const val PREFERENCES = "sentinel_sms_callback_progress"
        const val MAX_TRACKED = 128
        const val TTL_MS = 24L * 60L * 60L * 1000L
        const val MAX_CLOCK_SKEW_MS = 5L * 60L * 1000L
        val LOCK = Any()
    }
}
