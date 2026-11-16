package com.sentinel.quantum.security

/**
 * Duplicate classification for files the user/app is already allowed to read.
 * Equal size is only a candidate signal. Hash evidence is accepted only when
 * it belongs to a stable canonical identity and unchanged file metadata.
 */
object SentinelDuplicatePolicy {
    data class FileEvidence(
        val stableId: String,
        val bytes: Long?,
        val sha256: String?,
        val canonicalId: String? = null,
        val modifiedAtMs: Long? = null,
        val hashedBytes: Long? = null,
        val hashedModifiedAtMs: Long? = null
    ) {
        init {
            require(stableId.isNotBlank())
            require(bytes == null || bytes >= 0L)
            require(sha256 == null || sha256.matches(Regex("[0-9a-fA-F]{64}")))
            require(canonicalId == null || canonicalId.isNotBlank())
            require(modifiedAtMs == null || modifiedAtMs >= 0L)
            require(hashedBytes == null || hashedBytes >= 0L)
            require(hashedModifiedAtMs == null || hashedModifiedAtMs >= 0L)
        }

        val hasFreshHashEvidence: Boolean
            get() = sha256 != null &&
                bytes != null &&
                modifiedAtMs != null &&
                hashedBytes == bytes &&
                hashedModifiedAtMs == modifiedAtMs
    }

    enum class Match {
        NOT_A_MATCH,
        SIZE_CANDIDATE,
        CONFIRMED_DUPLICATE,
        UNKNOWN
    }

    fun compare(left: FileEvidence, right: FileEvidence): Match {
        if (left.stableId == right.stableId) return Match.NOT_A_MATCH
        if (left.canonicalId != null && left.canonicalId == right.canonicalId) return Match.NOT_A_MATCH

        val leftBytes = left.bytes ?: return Match.UNKNOWN
        val rightBytes = right.bytes ?: return Match.UNKNOWN
        if (leftBytes != rightBytes) return Match.NOT_A_MATCH

        if (!left.hasFreshHashEvidence || !right.hasFreshHashEvidence) return Match.SIZE_CANDIDATE
        return if (left.sha256.equals(right.sha256, ignoreCase = true)) {
            Match.CONFIRMED_DUPLICATE
        } else {
            Match.NOT_A_MATCH
        }
    }
}
