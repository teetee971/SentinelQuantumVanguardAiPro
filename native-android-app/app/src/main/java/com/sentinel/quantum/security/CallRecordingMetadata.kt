package com.sentinel.quantum.security

/**
 * Local-only metadata for a recorded call. Audio bytes are never stored here.
 * Phone numbers are represented only by a local fingerprint.
 */
data class CallRecordingMetadata(
    val id: String,
    val numberFingerprint: String,
    val startedAtEpochMs: Long,
    val durationMs: Long,
    val captureLevel: CallRecorderCapability.CaptureLevel,
    val favorite: Boolean = false,
    val note: String = "",
    val tags: Set<String> = emptySet()
) {
    init {
        require(id.isNotBlank())
        require(numberFingerprint.isNotBlank())
        require(startedAtEpochMs >= 0)
        require(durationMs >= 0)
        require(note.length <= MAX_NOTE_LENGTH)
        require(tags.size <= MAX_TAGS)
        require(tags.all { it.isNotBlank() && it.length <= MAX_TAG_LENGTH })
    }

    companion object {
        const val MAX_NOTE_LENGTH = 500
        const val MAX_TAGS = 12
        const val MAX_TAG_LENGTH = 32

        fun sanitizeNote(raw: String): String =
            raw.trim().replace(Regex("\\s+"), " ").take(MAX_NOTE_LENGTH)

        fun sanitizeTags(raw: Iterable<String>): Set<String> =
            raw.asSequence()
                .map { it.trim().replace(Regex("\\s+"), " ").take(MAX_TAG_LENGTH) }
                .filter { it.isNotBlank() }
                .distinct()
                .take(MAX_TAGS)
                .toSet()
    }
}

object CallRecordingRetention {
    enum class Policy { HOURS_24, DAYS_7, DAYS_30, DAYS_90, KEEP }

    fun expiresAt(startedAtEpochMs: Long, policy: Policy): Long? {
        if (policy == Policy.KEEP) return null
        val delta = when (policy) {
            Policy.HOURS_24 -> 24L * 60 * 60 * 1000
            Policy.DAYS_7 -> 7L * 24 * 60 * 60 * 1000
            Policy.DAYS_30 -> 30L * 24 * 60 * 60 * 1000
            Policy.DAYS_90 -> 90L * 24 * 60 * 60 * 1000
            Policy.KEEP -> return null
        }
        return if (Long.MAX_VALUE - startedAtEpochMs < delta) Long.MAX_VALUE
        else startedAtEpochMs + delta
    }

    fun isExpired(startedAtEpochMs: Long, policy: Policy, nowEpochMs: Long): Boolean =
        expiresAt(startedAtEpochMs, policy)?.let { nowEpochMs >= it } ?: false
}
