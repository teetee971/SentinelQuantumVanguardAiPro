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
            .map { group ->
                val distinct = group.distinctBy { it.stableId }
                val sizes = distinct.map { it.bytes }
                val reclaimable = if (sizes.all { it != null } && sizes.distinct().size == 1) {
                    sizes.first()!! * (distinct.size - 1L)
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
