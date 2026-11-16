package com.sentinel.quantum.security

/**
 * Resource guard for duplicate verification. Large files remain candidates
 * until the user explicitly requests deeper verification.
 */
object SentinelDuplicateHashPolicy {
    const val AUTO_HASH_MAX_BYTES: Long = 256L * 1024L * 1024L

    enum class Decision {
        AUTO_HASH,
        REQUIRE_EXPLICIT_DEEP_SCAN,
        UNKNOWN_SIZE
    }

    fun decide(bytes: Long?): Decision = when {
        bytes == null -> Decision.UNKNOWN_SIZE
        bytes < 0L -> Decision.UNKNOWN_SIZE
        bytes <= AUTO_HASH_MAX_BYTES -> Decision.AUTO_HASH
        else -> Decision.REQUIRE_EXPLICIT_DEEP_SCAN
    }
}
