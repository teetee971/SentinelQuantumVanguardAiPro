package com.sentinel.quantum.security

/**
 * Converts already-pseudonymised network observations into timeline events.
 *
 * Raw MAC/BSSID values must be converted to a device-bound fingerprint before this adapter is
 * called. The adapter deliberately accepts only the bounded hexadecimal identity accepted by
 * [NetworkChangeTimeline].
 */
object NetworkObservationTimelineAdapter {
    fun events(
        source: NetworkTimelineSource,
        fingerprints: List<String>,
        knownFingerprints: Set<String>,
        observedAtMs: Long
    ): List<NetworkTimelineEvent> {
        if (observedAtMs < 0L) return emptyList()

        val known = knownFingerprints.asSequence().map { it.trim().lowercase() }.toSet()
        return fingerprints
            .asSequence()
            .map { it.trim().lowercase() }
            .filter(::isFingerprint)
            .distinct()
            .sorted()
            .map { fingerprint ->
                NetworkTimelineEvent(
                    subjectFingerprint = fingerprint,
                    kind = if (fingerprint in known) {
                        NetworkTimelineEventKind.RETURNED
                    } else {
                        NetworkTimelineEventKind.FIRST_SEEN
                    },
                    source = source,
                    severity = NetworkTimelineSeverity.INFO,
                    observedAtMs = observedAtMs,
                    summary = when (source) {
                        NetworkTimelineSource.ANDROID_WIFI -> "Réseau Wi-Fi observé localement"
                        NetworkTimelineSource.ANDROID_BLE -> "Appareil Bluetooth observé localement"
                        else -> "Observation réseau locale"
                    }
                )
            }
            .toList()
    }

    private fun isFingerprint(value: String): Boolean =
        value.length == FINGERPRINT_LENGTH && value.all { it in '0'..'9' || it in 'a'..'f' }

    private const val FINGERPRINT_LENGTH = 64
}
