package com.sentinel.quantum.security

/**
 * Groups only duplicates backed by fresh hash evidence. One copy per group is
 * always retained in the reclaimable estimate. Canonical aliases never count
 * as separate copies.
 */
object SentinelDuplicateGroups {
    data class Group(
        val sha256: String,
        val files: List<SentinelDuplicatePolicy.FileEvidence>,
        val reclaimableBytesIfKeepingOne: Long?
    )

    fun confirmed(files: List<SentinelDuplicatePolicy.FileEvidence>): List<Group> =
        files.asSequence()
            .filter { it.hasFreshHashEvidence }
            .groupBy { it.sha256!!.lowercase() }
            .values
            .mapNotNull { group ->
                val distinct = group
                    .distinctBy { it.stableId }
                    .distinctBy { it.canonicalId ?: "stable:${it.stableId}" }

                if (distinct.size < 2) return@mapNotNull null

                val knownSizes = distinct.mapNotNull { it.bytes }.distinct()
                if (knownSizes.size > 1) return@mapNotNull null

                val reclaimable = if (distinct.all { it.bytes != null } && knownSizes.size == 1) {
                    knownSizes.single() * (distinct.size - 1L)
                } else {
                    null
                }

                Group(
                    sha256 = distinct.first().sha256!!.lowercase(),
                    files = distinct,
                    reclaimableBytesIfKeepingOne = reclaimable
                )
            }
            .toList()
}
