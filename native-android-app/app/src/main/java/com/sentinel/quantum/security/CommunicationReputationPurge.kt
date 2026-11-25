package com.sentinel.quantum.security

/**
 * Selects only the reputation cache entries. The install HMAC secret lives in another
 * preferences file and is never part of this set.
 */
internal object CommunicationReputationPurge {
    const val ENTRY_PREFIX = "h1:"

    fun keysToRemove(existing: Set<String>): Set<String> =
        existing.filter { it.startsWith(ENTRY_PREFIX) }.toSet()
}
