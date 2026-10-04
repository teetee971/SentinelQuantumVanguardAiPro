package com.sentinel.quantum.security

/** Pure classification for irreversible exact-number fingerprints across canonicalization eras. */
internal object ExactRuleMigrationPolicy {
    data class Partition(
        val activeSafe: Set<String>,
        val quarantinedLegacy: Set<String>
    )

    fun partition(
        hashes: Set<String>,
        metadataByHash: Map<String, CallBlockMetadata.Entry>,
        nowEpochMs: Long
    ): Partition {
        val activeSafe = linkedSetOf<String>()
        val quarantinedLegacy = linkedSetOf<String>()

        hashes.forEach { hash ->
            val metadata = metadataByHash[hash]
            if (metadata != null && !metadata.isActive(nowEpochMs)) return@forEach
            if (metadata?.safeForExactMatching == true) {
                activeSafe += hash
            } else {
                quarantinedLegacy += hash
            }
        }

        return Partition(activeSafe, quarantinedLegacy)
    }

    /** Current-schema hashes may be cleared by ordinary user rule management, expired or not. */
    fun currentSchemaHashes(
        hashes: Set<String>,
        metadataByHash: Map<String, CallBlockMetadata.Entry>
    ): Set<String> = hashes.filterTo(linkedSetOf()) { hash ->
        metadataByHash[hash]?.safeForExactMatching == true
    }

    /** Missing, malformed, or pre-migration metadata always remains on the explicit migration path. */
    fun legacyHashes(
        hashes: Set<String>,
        metadataByHash: Map<String, CallBlockMetadata.Entry>
    ): Set<String> = hashes.filterTo(linkedSetOf()) { hash ->
        metadataByHash[hash]?.safeForExactMatching != true
    }
}
