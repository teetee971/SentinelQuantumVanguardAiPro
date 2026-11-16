package com.sentinel.quantum.security

/**
 * Persists only the bounded, freshness-qualified subset of a consented caller reputation result.
 *
 * The cache remains responsible for phone canonicalization, opaque key derivation, validation and
 * storage-failure bounding. This writer deliberately contains no Android or network dependency.
 */
class CommunicationReputationWriter(
    private val cache: CommunicationReputationCache
) {
    fun persist(
        rawNumber: String,
        result: CallerReputationClient.Result,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        val entry = CommunicationReputationBridge.entryFrom(result) ?: return false
        return cache.put(rawNumber, entry, nowMs)
    }
}
