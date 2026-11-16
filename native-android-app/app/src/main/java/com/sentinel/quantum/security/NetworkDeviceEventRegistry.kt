package com.sentinel.quantum.security

/**
 * Local-only event registry for devices observed on networks owned by the user or
 * explicitly authorized for administration.
 *
 * The registry accepts only a device-bound fingerprint. Raw MAC/BSSID values, payloads
 * and credentials are intentionally outside this model.
 */
enum class NetworkDeviceEventType {
    FIRST_SEEN,
    RETURNED,
    DISAPPEARED,
    IDENTITY_CHANGED,
    RISK_CHANGED
}

enum class NetworkDeviceObservationSource {
    ANDROID_WIFI,
    ANDROID_BLE,
    LOCAL_AGENT,
    USER
}

data class NetworkDeviceEvent(
    val deviceFingerprint: String,
    val type: NetworkDeviceEventType,
    val source: NetworkDeviceObservationSource,
    val observedAtMs: Long,
    val confidencePercent: Int
)

data class NetworkDeviceRegistryEntry(
    val deviceFingerprint: String,
    val firstSeenAtMs: Long,
    val lastSeenAtMs: Long,
    val lastEventType: NetworkDeviceEventType,
    val sources: Set<NetworkDeviceObservationSource>,
    val maxConfidencePercent: Int,
    val eventCount: Int
)

data class NetworkDeviceRegistrySnapshot(
    val acceptedEvents: Int,
    val rejectedEvents: Int,
    val entries: List<NetworkDeviceRegistryEntry>
)

object NetworkDeviceEventRegistry {
    private const val MAX_EVENTS = 10_000
    private const val FINGERPRINT_HEX_LENGTH = 64
    private const val DUPLICATE_WINDOW_MS = 60_000L

    fun reduce(events: List<NetworkDeviceEvent>): NetworkDeviceRegistrySnapshot {
        val bounded = events.take(MAX_EVENTS)
        val rejectedForLimit = events.size - bounded.size

        val valid = bounded
            .filter(::isValid)
            .sortedBy { it.observedAtMs }

        val rejectedInvalid = bounded.size - valid.size
        val deduplicated = mutableListOf<NetworkDeviceEvent>()
        val lastBySignature = mutableMapOf<Triple<String, NetworkDeviceEventType, NetworkDeviceObservationSource>, Long>()

        valid.forEach { event ->
            val normalizedFingerprint = event.deviceFingerprint.lowercase()
            val signature = Triple(normalizedFingerprint, event.type, event.source)
            val previousAt = lastBySignature[signature]
            if (previousAt == null || event.observedAtMs - previousAt >= DUPLICATE_WINDOW_MS) {
                deduplicated += event.copy(deviceFingerprint = normalizedFingerprint)
                lastBySignature[signature] = event.observedAtMs
            }
        }

        val rejectedDuplicates = valid.size - deduplicated.size

        val entries = deduplicated
            .groupBy { it.deviceFingerprint }
            .map { (fingerprint, deviceEvents) ->
                NetworkDeviceRegistryEntry(
                    deviceFingerprint = fingerprint,
                    firstSeenAtMs = deviceEvents.minOf { it.observedAtMs },
                    lastSeenAtMs = deviceEvents.maxOf { it.observedAtMs },
                    lastEventType = deviceEvents.maxBy { it.observedAtMs }.type,
                    sources = deviceEvents.map { it.source }.toSet(),
                    maxConfidencePercent = deviceEvents.maxOf { it.confidencePercent },
                    eventCount = deviceEvents.size
                )
            }
            .sortedByDescending { it.lastSeenAtMs }

        return NetworkDeviceRegistrySnapshot(
            acceptedEvents = deduplicated.size,
            rejectedEvents = rejectedForLimit + rejectedInvalid + rejectedDuplicates,
            entries = entries
        )
    }

    private fun isValid(event: NetworkDeviceEvent): Boolean {
        if (event.observedAtMs < 0L) return false
        if (event.confidencePercent !in 0..100) return false
        return event.deviceFingerprint.length == FINGERPRINT_HEX_LENGTH &&
            event.deviceFingerprint.all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }
    }
}
