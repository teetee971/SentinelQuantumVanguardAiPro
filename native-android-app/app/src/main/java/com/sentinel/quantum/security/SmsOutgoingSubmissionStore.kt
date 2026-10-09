package com.sentinel.quantum.security

import android.content.Context
import android.content.SharedPreferences

/**
 * Bounded app-private ledger for SMS requests handed to Android telephony.
 * Only opaque callback correlation and age are retained; payload content is never stored here.
 */
class SmsOutgoingSubmissionStore internal constructor(
    private val preferences: SharedPreferences
) {
    constructor(context: Context) : this(
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    )

    data class Submission(
        val sendToken: Int,
        val providerMessageId: Long,
        val createdAtMs: Long
    )

    /**
     * Fail-closed admission signal used immediately before a new radio submission.
     * Corrupt/unprunable state is treated as pending rather than allowing an ambiguous duplicate.
     */
    fun hasPendingSubmission(nowMs: Long = System.currentTimeMillis()): Boolean = synchronized(LOCK) {
        if (nowMs <= 0L) return@synchronized true
        if (!validateEntries()) return@synchronized true
        if (!prune(nowMs)) return@synchronized true
        trackedCount() > 0
    }

    fun register(
        sendToken: Int,
        providerMessageId: Long,
        partCount: Int,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean = synchronized(LOCK) {
        if (
            sendToken <= 0 ||
            providerMessageId <= 0L ||
            partCount !in 1..SmsCallbackProgress.MAX_PARTS ||
            nowMs <= 0L
        ) return@synchronized false

        if (!validateEntries()) return@synchronized false
        if (!prune(nowMs)) return@synchronized false
        val storageKey = key(sendToken, providerMessageId)
        if (!preferences.contains(storageKey) && trackedCount() >= MAX_TRACKED) return@synchronized false
        preferences.edit()
            .putString(storageKey, "$nowMs|$partCount")
            .commit()
    }

    fun stale(nowMs: Long = System.currentTimeMillis()): List<Submission> = synchronized(LOCK) {
        if (!validateEntries()) {
            throw IllegalStateException("SMS submission ledger contains corrupt recovery state")
        }
        preferences.all.entries.mapNotNull { (key, value) ->
            val submission = decode(key, value as? String) ?: return@mapNotNull null
            if (nowMs - submission.createdAtMs < CALLBACK_TIMEOUT_MS) null else submission
        }.take(MAX_TRACKED)
    }

    fun remove(sendToken: Int, providerMessageId: Long): Boolean = synchronized(LOCK) {
        val storageKey = key(sendToken, providerMessageId)
        if (!preferences.contains(storageKey)) return@synchronized true
        preferences.edit().remove(storageKey).commit()
    }

    private fun prune(nowMs: Long): Boolean {
        val entries = preferences.all.entries
        if (entries.any { (key, value) -> decode(key, value as? String) == null }) return false
        val expired = entries.filter { (key, value) ->
            val submission = decode(key, value as String) ?: return@filter false
            nowMs - submission.createdAtMs > RETENTION_MS
        }
        if (expired.isEmpty()) return true
        val editor = preferences.edit()
        expired.forEach { editor.remove(it.key) }
        return editor.commit()
    }

    private fun validateEntries(): Boolean =
        preferences.all.entries.all { (key, value) ->
            decode(key, value as? String) != null
        }

    private fun trackedCount(): Int = preferences.all.count { (key, value) ->
        decode(key, value as? String) != null
    }

    private fun decode(storageKey: String, raw: String?): Submission? {
        val ids = storageKey.split(":", limit = 2)
        if (ids.size != 2) return null
        val sendToken = ids[0].toIntOrNull()?.takeIf { it > 0 } ?: return null
        val providerMessageId = ids[1].toLongOrNull()?.takeIf { it > 0L } ?: return null
        val parts = raw.orEmpty().split("|", limit = 2)
        val createdAtMs = parts.getOrNull(0)?.toLongOrNull()?.takeIf { it > 0L } ?: return null
        parts.getOrNull(1)?.toIntOrNull()?.takeIf {
            it in 1..SmsCallbackProgress.MAX_PARTS
        } ?: return null
        return Submission(sendToken, providerMessageId, createdAtMs)
    }

    private fun key(sendToken: Int, providerMessageId: Long) = "$sendToken:$providerMessageId"

    companion object {
        const val CALLBACK_TIMEOUT_MS = 15L * 60L * 1000L
        private const val RETENTION_MS = 24L * 60L * 60L * 1000L
        private const val MAX_TRACKED = 128
        private const val PREFERENCES = "sentinel_sms_outgoing_submissions_v1"
        private val LOCK = Any()
    }
}
