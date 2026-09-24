package com.sentinel.quantum.security

/** Maps an already-computed snapshot diff into privacy-bounded local timeline events. */
object NetworkSnapshotTimelineAdapter {
    fun toEvents(diff: NetworkSnapshotDiff): List<NetworkTimelineEvent> {
        val at = diff.currentObservedAtMs
        val events = mutableListOf<NetworkTimelineEvent>()

        diff.addedDevices.sorted().forEach { fp ->
            events += event(fp, NetworkTimelineEventKind.FIRST_SEEN, NetworkTimelineSeverity.INFO, at, "Appareil observé dans le nouvel état réseau")
        }
        diff.removedDevices.sorted().forEach { fp ->
            events += event(fp, NetworkTimelineEventKind.DISAPPEARED, NetworkTimelineSeverity.INFO, at, "Appareil absent du nouvel état réseau")
        }

        val topologySubjects = (diff.addedTopologyEdges + diff.removedTopologyEdges)
            .flatMap { listOf(it.fromFingerprint, it.toFingerprint) }
            .toSortedSet()
        topologySubjects.forEach { fp ->
            events += event(fp, NetworkTimelineEventKind.TOPOLOGY_CHANGE, NetworkTimelineSeverity.WARNING, at, "Topologie réseau modifiée")
        }

        diff.riskIncreases.forEach { delta ->
            events += event(
                delta.subjectFingerprint,
                NetworkTimelineEventKind.RISK_SCORE_CHANGED,
                if (diff.materialRiskIncreases.any { it.subjectFingerprint == delta.subjectFingerprint }) NetworkTimelineSeverity.HIGH else NetworkTimelineSeverity.WARNING,
                at,
                "Score de risque augmenté de ${delta.delta} points"
            )
        }
        diff.riskDecreases.forEach { delta ->
            events += event(delta.subjectFingerprint, NetworkTimelineEventKind.RISK_SCORE_CHANGED, NetworkTimelineSeverity.INFO, at, "Score de risque diminué de ${-delta.delta} points")
        }

        return events.sortedWith(compareBy<NetworkTimelineEvent> { it.subjectFingerprint }.thenBy { it.kind.name }.thenBy { it.summary })
    }

    private fun event(
        fingerprint: String,
        kind: NetworkTimelineEventKind,
        severity: NetworkTimelineSeverity,
        at: Long,
        summary: String
    ) = NetworkTimelineEvent(
        subjectFingerprint = fingerprint,
        kind = kind,
        source = NetworkTimelineSource.TOPOLOGY_ENGINE,
        severity = severity,
        observedAtMs = at,
        summary = summary
    )
}
