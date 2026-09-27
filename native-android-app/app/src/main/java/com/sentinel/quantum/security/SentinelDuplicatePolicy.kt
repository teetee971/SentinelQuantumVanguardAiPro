package com.sentinel.quantum.security

/**
 * Duplicate classification for files the user/app is already allowed to read.
 * Equal size is only a candidate signal; a duplicate is confirmed only when
 * content hashes are available and equal.
 */
object SentinelDuplicatePolicy {
    data class FileEvidence(
        val stableId: String,
        val bytes: Long?,
        val sha256: String?
    ) {
        init {
            require(stableId.isNotBlank())
            require(bytes == null || bytes >= 0L)
            require(sha256 == null || sha256.matches(Regex("[0-9a-fA-F]{64}")))
        }
    }

    enum class Match {
        NOT_A_MATCH,
        SIZE_CANDIDATE,
        CONFIRMED_DUPLICATE,
        UNKNOWN
    }

    fun compare(left: FileEvidence, right: FileEvidence): Match {
        if (left.stableId == right.stableId) return Match.NOT_A_MATCH
        val leftBytes = left.bytes ?: return Match.UNKNOWN
        val rightBytes = right.bytes ?: return Match.UNKNOWN
        if (leftBytes != rightBytes) return Match.NOT_A_MATCH

        val leftHash = left.sha256
        val rightHash = right.sha256
        if (leftHash == null || rightHash == null) return Match.SIZE_CANDIDATE
        return if (leftHash.equals(rightHash, ignoreCase = true)) {
            Match.CONFIRMED_DUPLICATE
        } else {
            Match.NOT_A_MATCH
        }
    }
}
