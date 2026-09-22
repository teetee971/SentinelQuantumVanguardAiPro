package com.sentinel.quantum.security

/**
 * Local-only chronology for already-classified network-security events.
 *
 * This component does not scan, capture packets, inspect payloads, open sockets or
 * resolve identities. It stores only device-bound fingerprints plus bounded metadata.
 */
enum class NetworkTimelineEventKind {
    FIRST_SEEN,
    RETURNED,
    DISAPPEARED,
    IDENTITY_CHANGE,
    TOPOLOGY_CHANGE,
    COVERT_DEVICE_HINT,
    FLOW_ANOMALY,
    BEHAVIOR_ANOMALY,
    RISK_SCORE_CHANGED
}

enum class NetworkTimelineSource {
    ANDROID_WIFI,
    ANDROID_BLE,
    LOCAL_AGENT,
    FLOW_ANALYZER,
    BEHAVIOR_BASELINE,
    TOPOLOGY_ENGINE,
    RISK_DOSSIER,
    USER_CONFIRMED
}

enum class NetworkTimelineSeverity {
    INFO,
    WARNING,
    HIGH
}

data class NetworkTimelineEvent(
    val subjectFingerprint: String,
    val kind: NetworkTimelineEventKind,
    val source: NetworkTimelineSource,
    val severity: NetworkTimelineSeverity,
    val observedAtMs: Long,
    val summary: String
)

data class NetworkTimelineSnapshot(
    val acceptedEvents: List<NetworkTimelineEvent>,
    val rejectedEvents: Int,
    val prunedEvents: Int,
    val firstObservedAtMs: Long?,
    val lastObservedAtMs: Long?,
    val highSeverityCount: Int,
    val warningCount: Int
)

object NetworkChangeTimeline {
    const val MAX_EVENTS = 10_000
    const val DEFAULT_RETENTION_MS = 30L * 24L * 60L * 60L * 1000L
    const val DUPLICATE_WINDOW_MS = 60_000L
    private const val FINGERPRINT_HEX_LENGTH = 64
    private const val MAX_SUMMARY_LENGTH = 160

    fun build(
        events: List<NetworkTimelineEvent>,
        nowMs: Long,
        retentionMs: Long = DEFAULT_RETENTION_MS
    ): NetworkTimelineSnapshot {
        require(nowMs >= 0L) { "nowMs must be non-negative" }
        require(retentionMs > 0L) { "retentionMs must be positive" }

        val bounded = events.takeLast(MAX_EVENTS)
        val rejectedForLimit = events.size - bounded.size
        val retentionFloor = (nowMs - retentionMs).coerceAtLeast(0L)

        val normalized = mutableListOf<NetworkTimelineEvent>()
        var rejected = rejectedForLimit
        var pruned = 0

        bounded.sortedBy { it.observedAtMs }.forEach { raw ->
            val event = normalize(raw, nowMs)
            if (event == null) {
                rejected += 1
                return@forEach
            }
            if (event.observedAtMs < retentionFloor) {
                pruned += 1
                return@forEach
            }

            val previous = normalized.lastOrNull {
                it.subjectFingerprint == event.subjectFingerprint &&
                    it.kind == event.kind &&
                    it.source == event.source &&
                    it.severity == event.severity &&
                    it.summary.equals(event.summary, ignoreCase = true)
            }

            if (previous != null && event.observedAtMs - previous.observedAtMs < DUPLICATE_WINDOW_MS) {
                rejected += 1
            } else {
                normalized += event
            }
        }

        val accepted = normalized.sortedWith(
            compareByDescending<NetworkTimelineEvent> { it.observedAtMs }
                .thenBy { it.subjectFingerprint }
                .thenBy { it.kind.name }
        )

        return NetworkTimelineSnapshot(
            acceptedEvents = accepted,
            rejectedEvents = rejected,
            prunedEvents = pruned,
            firstObservedAtMs = accepted.minOfOrNull { it.observedAtMs },
            lastObservedAtMs = accepted.maxOfOrNull { it.observedAtMs },
            highSeverityCount = accepted.count { it.severity == NetworkTimelineSeverity.HIGH },
            warningCount = accepted.count { it.severity == NetworkTimelineSeverity.WARNING }
        )
    }

    private fun normalize(
        event: NetworkTimelineEvent,
        nowMs: Long
    ): NetworkTimelineEvent? {
        val fingerprint = event.subjectFingerprint.trim().lowercase()
        if (fingerprint.length != FINGERPRINT_HEX_LENGTH) return null
        if (!fingerprint.all { it in '0'..'9' || it in 'a'..'f' }) return null
        if (event.observedAtMs !in 0L..nowMs) return null

        val summary = event.summary
            .trim()
            .replace(Regex("\\s+"), " ")
            .take(MAX_SUMMARY_LENGTH)
        if (summary.isEmpty()) return null

        return event.copy(
            subjectFingerprint = fingerprint,
            summary = summary
        )
    }
}
