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
}
