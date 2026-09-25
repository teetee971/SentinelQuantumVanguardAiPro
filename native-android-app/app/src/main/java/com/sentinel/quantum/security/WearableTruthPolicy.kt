package com.sentinel.quantum.security

/**
 * Vendor-neutral wearable capability model.
 *
 * Detection, pairing and Sentinel integration are deliberately separate truths:
 * seeing a Bluetooth wearable never implies that Sentinel controls or synchronises it.
 */
enum class WearableTransport { BLUETOOTH_CLASSIC, BLUETOOTH_LE, WEAR_OS, VENDOR_BRIDGE }

enum class WearableIntegrationState {
    DISCOVERED,
    BONDED,
    COMPANION_ASSOCIATED,
    SENTINEL_CONNECTED
}

enum class WearableCapability {
    CONNECTION_STATE,
    SENTINEL_ALERTS,
    CALL_RISK_ALERTS,
    SMS_RISK_ALERTS,
    QUICK_ACTIONS,
    BATTERY_STATUS
}

data class WearableObservation(
    val stableId: String,
    val displayName: String?,
    val transports: Set<WearableTransport>,
    val bonded: Boolean,
    val companionAssociated: Boolean,
    val sentinelChannelConnected: Boolean,
    val capabilities: Set<WearableCapability>
)

data class WearableTruth(
    val state: WearableIntegrationState,
    val capabilities: Set<WearableCapability>
)

object WearableTruthPolicy {
    fun evaluate(observation: WearableObservation): WearableTruth {
        val state = when {
            observation.sentinelChannelConnected -> WearableIntegrationState.SENTINEL_CONNECTED
            observation.companionAssociated -> WearableIntegrationState.COMPANION_ASSOCIATED
            observation.bonded -> WearableIntegrationState.BONDED
            else -> WearableIntegrationState.DISCOVERED
        }

        // Sentinel-specific actions are truthful only with a live Sentinel channel.
        val safeCapabilities = if (observation.sentinelChannelConnected) {
            observation.capabilities
        } else {
            observation.capabilities - setOf(
                WearableCapability.SENTINEL_ALERTS,
                WearableCapability.CALL_RISK_ALERTS,
                WearableCapability.SMS_RISK_ALERTS,
                WearableCapability.QUICK_ACTIONS
            )
        }
        return WearableTruth(state, safeCapabilities)
    }
}
