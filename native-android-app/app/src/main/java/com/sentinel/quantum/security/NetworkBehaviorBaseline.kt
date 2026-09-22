package com.sentinel.quantum.security

/**
 * Deterministic local baseline for already-observed network metadata.
 *
 * This component does not capture packets, inspect payloads, decrypt TLS, open sockets
 * or contact a remote service. It compares a current metadata sample with bounded
 * historical metadata for the same device-bound fingerprint.
 */
enum class NetworkBehaviorProtocol {
    TCP,
    UDP,
    ICMP,
    OTHER
}

enum class NetworkBehaviorAnomaly {
    NEW_DESTINATION,
    NEW_SERVICE,
    UNUSUAL_HOUR,
    VOLUME_SPIKE
}

data class NetworkBehaviorSample(
    val subjectFingerprint: String,
    val destination: String,
    val remotePort: Int?,
    val protocol: NetworkBehaviorProtocol,
    val totalBytes: Long,
    val observedHourUtc: Int
)

data class NetworkBehaviorAssessment(
    val baselineReady: Boolean,
    val baselineSampleCount: Int,
    val anomalies: Set<NetworkBehaviorAnomaly>,
    val riskScore: Int,
    val medianHistoricalBytes: Long?,
    val reason: String
)

object NetworkBehaviorBaseline {
    const val MIN_BASELINE_SAMPLES = 5
    const val MIN_HOUR_BASELINE_SAMPLES = 12
    const val MAX_HISTORY_SAMPLES = 5_000
    private const val FINGERPRINT_HEX_LENGTH = 64
    private const val VOLUME_MULTIPLIER = 4L
    private const val MIN_VOLUME_DELTA_BYTES = 256L * 1024L

    fun assess(
        history: List<NetworkBehaviorSample>,
        current: NetworkBehaviorSample
    ): NetworkBehaviorAssessment {
        val normalizedCurrent = normalize(current)
            ?: return NetworkBehaviorAssessment(
                baselineReady = false,
                baselineSampleCount = 0,
                anomalies = emptySet(),
                riskScore = 0,
                medianHistoricalBytes = null,
                reason = "Échantillon courant invalide."
            )

        val boundedHistory = history
            .takeLast(MAX_HISTORY_SAMPLES)
            .mapNotNull(::normalize)
            .filter { it.subjectFingerprint == normalizedCurrent.subjectFingerprint }

        if (boundedHistory.size < MIN_BASELINE_SAMPLES) {
            return NetworkBehaviorAssessment(
                baselineReady = false,
                baselineSampleCount = boundedHistory.size,
                anomalies = emptySet(),
                riskScore = 0,
                medianHistoricalBytes = medianBytes(boundedHistory),
                reason = "Historique insuffisant pour établir un comportement de référence."
            )
        }

        val anomalies = linkedSetOf<NetworkBehaviorAnomaly>()

        val knownDestinations = boundedHistory.map { it.destination }.toSet()
        if (normalizedCurrent.destination !in knownDestinations) {
            anomalies += NetworkBehaviorAnomaly.NEW_DESTINATION
        }

        val knownServices = boundedHistory
            .map { Triple(it.destination, it.protocol, it.remotePort) }
            .toSet()
        val currentService = Triple(
            normalizedCurrent.destination,
            normalizedCurrent.protocol,
            normalizedCurrent.remotePort
        )
        if (currentService !in knownServices) {
            anomalies += NetworkBehaviorAnomaly.NEW_SERVICE
        }

        if (boundedHistory.size >= MIN_HOUR_BASELINE_SAMPLES) {
            val observedHours = boundedHistory.map { it.observedHourUtc }.toSet()
            if (normalizedCurrent.observedHourUtc !in observedHours) {
                anomalies += NetworkBehaviorAnomaly.UNUSUAL_HOUR
            }
        }

        val medianBytes = medianBytes(boundedHistory) ?: 0L
        if (medianBytes > 0L) {
            val multiplierThreshold = saturatingMultiply(medianBytes, VOLUME_MULTIPLIER)
            val deltaThreshold = saturatingAdd(medianBytes, MIN_VOLUME_DELTA_BYTES)
            val threshold = maxOf(multiplierThreshold, deltaThreshold)
            if (normalizedCurrent.totalBytes >= threshold) {
                anomalies += NetworkBehaviorAnomaly.VOLUME_SPIKE
            }
        }

        val score = anomalies.sumOf {
            when (it) {
                NetworkBehaviorAnomaly.NEW_DESTINATION -> 25
                NetworkBehaviorAnomaly.NEW_SERVICE -> 20
                NetworkBehaviorAnomaly.UNUSUAL_HOUR -> 15
                NetworkBehaviorAnomaly.VOLUME_SPIKE -> 30
            }
        }.coerceIn(0, 100)

        return NetworkBehaviorAssessment(
            baselineReady = true,
            baselineSampleCount = boundedHistory.size,
            anomalies = anomalies,
            riskScore = score,
            medianHistoricalBytes = medianBytes,
            reason = if (anomalies.isEmpty()) {
                "Aucun écart déterministe détecté par rapport à l’historique local."
            } else {
                "Écart détecté par rapport à l’historique local ; vérification utilisateur recommandée."
            }
        )
    }

    private fun normalize(sample: NetworkBehaviorSample): NetworkBehaviorSample? {
        val fingerprint = sample.subjectFingerprint.trim().lowercase()
        if (fingerprint.length != FINGERPRINT_HEX_LENGTH) return null
        if (!fingerprint.all { it in '0'..'9' || it in 'a'..'f' }) return null

        val destination = sample.destination.trim().lowercase().take(253)
        if (destination.isEmpty()) return null
        if (sample.remotePort != null && sample.remotePort !in 1..65535) return null
        if (sample.totalBytes < 0L) return null
        if (sample.observedHourUtc !in 0..23) return null

        return sample.copy(
            subjectFingerprint = fingerprint,
            destination = destination
        )
    }

    private fun medianBytes(samples: List<NetworkBehaviorSample>): Long? {
        if (samples.isEmpty()) return null
        val sorted = samples.map { it.totalBytes }.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            val left = sorted[middle - 1]
            val right = sorted[middle]
            left + (right - left) / 2L
        }
    }

    private fun saturatingMultiply(value: Long, multiplier: Long): Long =
        if (value > Long.MAX_VALUE / multiplier) Long.MAX_VALUE else value * multiplier

    private fun saturatingAdd(left: Long, right: Long): Long =
        if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right
}
