package com.sentinel.quantum.security

/**
 * Vendor-neutral contract for a future wearable transport adapter.
 *
 * Adapter observations are untrusted platform facts. They never manufacture Sentinel
 * authentication, session authority or cryptographic proof.
 */
enum class WearableAdapterAvailability {
    UNAVAILABLE,
    AVAILABLE
}

data class WearableAdapterObservation(
    val stableIdHint: String?,
    val displayName: String?,
    val transport: WearableTransport,
    val platformLinkState: WearablePlatformLinkState,
    val availability: WearableAdapterAvailability,
    val observedCapabilities: Set<WearableCapability>
)

data class WearableAdapterTruth(
    val transport: WearableTransport,
    val platformLinkState: WearablePlatformLinkState,
    val adapterAvailable: Boolean,
    val sentinelReachable: Boolean,
    val usableCapabilities: Set<WearableCapability>
)

object WearableAdapterPolicy {
    private val sentinelPrivilegedCapabilities = setOf(
        WearableCapability.SENTINEL_ALERTS,
        WearableCapability.CALL_RISK_ALERTS,
        WearableCapability.SMS_RISK_ALERTS,
        WearableCapability.QUICK_ACTIONS
    )

    fun evaluate(
        observation: WearableAdapterObservation,
        channelProof: WearableChannelProof?,
        negotiatedCapabilities: Set<WearableCapability>,
        nowMs: Long
    ): WearableAdapterTruth {
        val available = observation.availability == WearableAdapterAvailability.AVAILABLE
        val reachable = available && channelProof?.isFresh(nowMs) == true
        val platformCapabilities = observation.observedCapabilities
        val negotiated = observation.observedCapabilities intersect negotiatedCapabilities
        val safeCapabilities = if (reachable) {
            platformCapabilities - sentinelPrivilegedCapabilities + (negotiated intersect sentinelPrivilegedCapabilities)
        } else {
            platformCapabilities - sentinelPrivilegedCapabilities
        }

        return WearableAdapterTruth(
            transport = observation.transport,
            platformLinkState = observation.platformLinkState,
            adapterAvailable = available,
            sentinelReachable = reachable,
            usableCapabilities = safeCapabilities
        )
    }
}
