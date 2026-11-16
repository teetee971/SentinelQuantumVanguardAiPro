package com.sentinel.quantum.security

/**
 * Local topology evidence for networks the user owns or is authorized to administer.
 *
 * The model stores only device-bound fingerprints. Radio proximity alone is never enough
 * to create a relationship; callers must provide an explicit evidence source.
 */
enum class NetworkTopologyRelation {
    ATTACHED_TO_GATEWAY,
    BRIDGED_BY,
    MANAGED_BY,
    PEERS_WITH
}

enum class NetworkTopologyEvidenceSource {
    DHCP_LEASE,
    ROUTER_TABLE,
    LOCAL_AGENT,
    MESH_CONTROL_PLANE,
    USER_CONFIRMED
}

data class NetworkTopologyEdge(
    val fromFingerprint: String,
    val toFingerprint: String,
    val relation: NetworkTopologyRelation,
    val source: NetworkTopologyEvidenceSource,
    val observedAtMs: Long,
    val confidencePercent: Int
)

data class NetworkTopologySnapshot(
    val acceptedEdges: List<NetworkTopologyEdge>,
    val rejectedEdges: Int,
    val nodeFingerprints: Set<String>
)

object NetworkTopologyEvidence {
    private const val FINGERPRINT_HEX_LENGTH = 64
    private const val MAX_EDGES = 10_000

    fun normalize(edges: List<NetworkTopologyEdge>): NetworkTopologySnapshot {
        val bounded = edges.take(MAX_EDGES)
        val rejectedForLimit = edges.size - bounded.size
        val normalized = mutableListOf<NetworkTopologyEdge>()
        var rejected = rejectedForLimit

        bounded.forEach { edge ->
            val candidate = normalizeEdge(edge)
            if (candidate == null) {
                rejected += 1
            } else if (normalized.none { sameEvidence(it, candidate) }) {
                normalized += candidate
            } else {
                rejected += 1
            }
        }

        return NetworkTopologySnapshot(
            acceptedEdges = normalized.sortedWith(
                compareBy<NetworkTopologyEdge> { it.observedAtMs }
                    .thenBy { it.fromFingerprint }
                    .thenBy { it.toFingerprint }
            ),
            rejectedEdges = rejected,
            nodeFingerprints = normalized
                .flatMap { listOf(it.fromFingerprint, it.toFingerprint) }
                .toSet()
        )
    }

    private fun normalizeEdge(edge: NetworkTopologyEdge): NetworkTopologyEdge? {
        if (edge.observedAtMs < 0L) return null
        if (edge.confidencePercent !in 0..100) return null

        var from = normalizeFingerprint(edge.fromFingerprint) ?: return null
        var to = normalizeFingerprint(edge.toFingerprint) ?: return null
        if (from == to) return null

        if (edge.relation == NetworkTopologyRelation.PEERS_WITH && from > to) {
            val swap = from
            from = to
            to = swap
        }

        return edge.copy(
            fromFingerprint = from,
            toFingerprint = to
        )
    }

    private fun normalizeFingerprint(value: String): String? {
        val normalized = value.trim().lowercase()
        if (normalized.length != FINGERPRINT_HEX_LENGTH) return null
        if (!normalized.all { it in '0'..'9' || it in 'a'..'f' }) return null
        return normalized
    }

    private fun sameEvidence(left: NetworkTopologyEdge, right: NetworkTopologyEdge): Boolean =
        left.fromFingerprint == right.fromFingerprint &&
            left.toFingerprint == right.toFingerprint &&
            left.relation == right.relation &&
            left.source == right.source
}
