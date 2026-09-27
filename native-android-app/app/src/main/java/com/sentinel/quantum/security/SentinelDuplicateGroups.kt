package com.sentinel.quantum.security

/**
 * Groups only hash-confirmed duplicates. One copy per group is always retained
 * in the reclaimable estimate.
 */
object SentinelDuplicateGroups {
    data class Group(
        val sha256: String,
        val files: List<SentinelDuplicatePolicy.FileEvidence>,
        val reclaimableBytesIfKeepingOne: Long?
    )

    fun confirmed(files: List<SentinelDuplicatePolicy.FileEvidence>): List<Group> =
        files.asSequence()
            .filter { it.sha256 != null }
            .groupBy { it.sha256!!.lowercase() }
            .values
            .filter { group -> group.map { it.stableId }.distinct().size > 1 }
            .mapNotNull { group ->
                val distinct = group.distinctBy { it.stableId }
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
