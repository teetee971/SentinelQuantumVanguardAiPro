package com.sentinel.quantum.security

/**
 * Local deterministic comparison of two already-produced network snapshots.
 *
 * The diff engine performs no discovery or network access. It accepts only device-bound
 * fingerprints and bounded metadata, allowing higher layers to explain what changed
 * between two points in time.
 */
enum class NetworkSnapshotProtocol {
    TCP,
    UDP,
    OTHER
}

enum class NetworkSnapshotRelation {
    ATTACHED_TO_GATEWAY,
    BRIDGED_BY,
    MANAGED_BY,
    PEERS_WITH
}

data class NetworkSnapshotService(
    val subjectFingerprint: String,
    val protocol: NetworkSnapshotProtocol,
    val port: Int
)

data class NetworkSnapshotTopologyEdge(
    val fromFingerprint: String,
    val toFingerprint: String,
    val relation: NetworkSnapshotRelation
)

data class NetworkSnapshotState(
    val observedAtMs: Long,
    val deviceFingerprints: Set<String>,
    val services: Set<NetworkSnapshotService>,
    val topologyEdges: Set<NetworkSnapshotTopologyEdge>,
    val riskScores: Map<String, Int>
)

data class NetworkRiskDelta(
    val subjectFingerprint: String,
    val previousScore: Int,
    val currentScore: Int
) {
    val delta: Int get() = currentScore - previousScore
}

data class NetworkSnapshotDiff(
    val previousObservedAtMs: Long,
    val currentObservedAtMs: Long,
    val addedDevices: Set<String>,
    val removedDevices: Set<String>,
    val openedServices: Set<NetworkSnapshotService>,
    val closedServices: Set<NetworkSnapshotService>,
    val addedTopologyEdges: Set<NetworkSnapshotTopologyEdge>,
    val removedTopologyEdges: Set<NetworkSnapshotTopologyEdge>,
    val riskIncreases: List<NetworkRiskDelta>,
    val riskDecreases: List<NetworkRiskDelta>,
    val materialRiskIncreases: List<NetworkRiskDelta>,
    val rejectedItems: Int
)

object NetworkSnapshotDiffEngine {
    const val MAX_DEVICES = 4_096
    const val MAX_SERVICES = 20_000
    const val MAX_TOPOLOGY_EDGES = 10_000
    const val DEFAULT_MATERIAL_RISK_DELTA = 20
    private const val FINGERPRINT_HEX_LENGTH = 64

    fun compare(
        previous: NetworkSnapshotState,
        current: NetworkSnapshotState,
        materialRiskDelta: Int = DEFAULT_MATERIAL_RISK_DELTA
    ): NetworkSnapshotDiff? {
        if (previous.observedAtMs < 0L) return null
        if (current.observedAtMs < previous.observedAtMs) return null
        if (materialRiskDelta !in 1..100) return null

        val previousNormalized = normalize(previous)
        val currentNormalized = normalize(current)

        val riskSubjects = (
            previousNormalized.riskScores.keys +
                currentNormalized.riskScores.keys
            ).toSortedSet()

        val riskDeltas = riskSubjects.mapNotNull { fingerprint ->
            val before = previousNormalized.riskScores[fingerprint]
            val after = currentNormalized.riskScores[fingerprint]
            if (before == null || after == null || before == after) {
                null
            } else {
                NetworkRiskDelta(fingerprint, before, after)
            }
        }

        val increases = riskDeltas
            .filter { it.delta > 0 }
            .sortedWith(compareByDescending<NetworkRiskDelta> { it.delta }.thenBy { it.subjectFingerprint })
        val decreases = riskDeltas
            .filter { it.delta < 0 }
            .sortedWith(compareBy<NetworkRiskDelta> { it.delta }.thenBy { it.subjectFingerprint })

        return NetworkSnapshotDiff(
            previousObservedAtMs = previous.observedAtMs,
            currentObservedAtMs = current.observedAtMs,
            addedDevices = currentNormalized.devices - previousNormalized.devices,
            removedDevices = previousNormalized.devices - currentNormalized.devices,
            openedServices = currentNormalized.services - previousNormalized.services,
            closedServices = previousNormalized.services - currentNormalized.services,
            addedTopologyEdges = currentNormalized.topologyEdges - previousNormalized.topologyEdges,
            removedTopologyEdges = previousNormalized.topologyEdges - currentNormalized.topologyEdges,
            riskIncreases = increases,
            riskDecreases = decreases,
            materialRiskIncreases = increases.filter { it.delta >= materialRiskDelta },
            rejectedItems = previousNormalized.rejectedItems + currentNormalized.rejectedItems
        )
    }

    private data class NormalizedSnapshot(
        val devices: Set<String>,
        val services: Set<NetworkSnapshotService>,
        val topologyEdges: Set<NetworkSnapshotTopologyEdge>,
        val riskScores: Map<String, Int>,
        val rejectedItems: Int
    )

    private fun normalize(snapshot: NetworkSnapshotState): NormalizedSnapshot {
        var rejected = 0

        val devices = linkedSetOf<String>()
        snapshot.deviceFingerprints
            .sorted()
            .take(MAX_DEVICES)
            .forEach { raw ->
                val fingerprint = normalizeFingerprint(raw)
                if (fingerprint == null) {
                    rejected += 1
                } else {
                    devices += fingerprint
                }
            }
        rejected += (snapshot.deviceFingerprints.size - MAX_DEVICES).coerceAtLeast(0)

        val services = linkedSetOf<NetworkSnapshotService>()
        snapshot.services
            .sortedWith(
                compareBy<NetworkSnapshotService> { it.subjectFingerprint }
                    .thenBy { it.protocol.name }
                    .thenBy { it.port }
            )
            .take(MAX_SERVICES)
            .forEach { raw ->
                val fingerprint = normalizeFingerprint(raw.subjectFingerprint)
                if (fingerprint == null || raw.port !in 1..65535) {
                    rejected += 1
                } else {
                    services += raw.copy(subjectFingerprint = fingerprint)
                }
            }
        rejected += (snapshot.services.size - MAX_SERVICES).coerceAtLeast(0)

        val topology = linkedSetOf<NetworkSnapshotTopologyEdge>()
        snapshot.topologyEdges
            .sortedWith(
                compareBy<NetworkSnapshotTopologyEdge> { it.fromFingerprint }
                    .thenBy { it.toFingerprint }
                    .thenBy { it.relation.name }
            )
            .take(MAX_TOPOLOGY_EDGES)
            .forEach { raw ->
                val normalizedFrom = normalizeFingerprint(raw.fromFingerprint)
                val normalizedTo = normalizeFingerprint(raw.toFingerprint)
                if (normalizedFrom == null || normalizedTo == null || normalizedFrom == normalizedTo) {
                    rejected += 1
                    return@forEach
                }

                var from: String = normalizedFrom
                var to: String = normalizedTo
                if (raw.relation == NetworkSnapshotRelation.PEERS_WITH && from > to) {
                    val swap = from
                    from = to
                    to = swap
                }

                topology += raw.copy(
                    fromFingerprint = from,
                    toFingerprint = to
                )
            }
        rejected += (snapshot.topologyEdges.size - MAX_TOPOLOGY_EDGES).coerceAtLeast(0)

        val risks = linkedMapOf<String, Int>()
        snapshot.riskScores.toSortedMap().forEach { (rawFingerprint, score) ->
            val fingerprint = normalizeFingerprint(rawFingerprint)
            if (fingerprint == null || score !in 0..100) {
                rejected += 1
            } else {
                risks[fingerprint] = score
            }
        }

        return NormalizedSnapshot(
            devices = devices,
            services = services,
            topologyEdges = topology,
            riskScores = risks,
            rejectedItems = rejected
        )
    }

    private fun normalizeFingerprint(value: String): String? {
        val normalized = value.trim().lowercase()
        if (normalized.length != FINGERPRINT_HEX_LENGTH) return null
        if (!normalized.all { it in '0'..'9' || it in 'a'..'f' }) return null
        return normalized
    }
}
