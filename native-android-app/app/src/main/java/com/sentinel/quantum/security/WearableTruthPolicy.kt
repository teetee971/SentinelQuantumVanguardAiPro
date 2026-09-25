package com.sentinel.quantum.security

/**
 * Vendor-neutral wearable truth model.
 *
 * Platform visibility and Sentinel channel trust are orthogonal. A Bluetooth bond or companion
 * association never proves that Sentinel can currently reach or trust the wearable.
 */
enum class WearableTransport { BLUETOOTH_CLASSIC, BLUETOOTH_LE, WEAR_OS, VENDOR_BRIDGE }

enum class WearablePlatformLinkState { NONE, DISCOVERED, BONDED, COMPANION_ASSOCIATED }

enum class WearableChannelState { NOT_NEGOTIATED, NEGOTIATING, AUTHENTICATED, ACTIVE, STALE }

enum class WearableCapability {
    CONNECTION_STATE,
    SENTINEL_ALERTS,
    CALL_RISK_ALERTS,
    SMS_RISK_ALERTS,
    QUICK_ACTIONS,
    BATTERY_STATUS
}

data class WearableChannelProof(
    val state: WearableChannelState,
    val lastProofAtMs: Long?,
    val ttlMs: Long
) {
    init {
        require(ttlMs > 0) { "ttlMs must be positive" }
        require(lastProofAtMs == null || lastProofAtMs >= 0) { "lastProofAtMs must be non-negative" }
    }

    fun isFresh(nowMs: Long): Boolean =
        state == WearableChannelState.ACTIVE &&
            lastProofAtMs != null &&
            nowMs >= lastProofAtMs &&
            nowMs - lastProofAtMs < ttlMs
}

data class WearableObservation(
    val stableId: String,
    val displayName: String?,
    val transports: Set<WearableTransport>,
    val platformLinkState: WearablePlatformLinkState,
    val channelProof: WearableChannelProof,
    val capabilities: Set<WearableCapability>
)

data class WearableTruth(
    val platformLinkState: WearablePlatformLinkState,
    val channelState: WearableChannelState,
    val sentinelReachable: Boolean,
    val capabilities: Set<WearableCapability>
)

object WearableTruthPolicy {
    private val sentinelCapabilities = setOf(
        WearableCapability.SENTINEL_ALERTS,
        WearableCapability.CALL_RISK_ALERTS,
        WearableCapability.SMS_RISK_ALERTS,
        WearableCapability.QUICK_ACTIONS
    )

    fun evaluate(observation: WearableObservation, nowMs: Long): WearableTruth {
        val reachable = observation.channelProof.isFresh(nowMs)
        val channelState = when {
            observation.channelProof.state == WearableChannelState.ACTIVE && !reachable ->
                WearableChannelState.STALE
            else -> observation.channelProof.state
        }
        val safeCapabilities = if (reachable) {
            observation.capabilities
        } else {
            observation.capabilities - sentinelCapabilities
        }
        return WearableTruth(
            platformLinkState = observation.platformLinkState,
            channelState = channelState,
            sentinelReachable = reachable,
            capabilities = safeCapabilities
        )
    }
}
