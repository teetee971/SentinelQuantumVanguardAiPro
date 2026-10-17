package com.sentinel.quantum.security

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/**
 * Phone-side receiver for the non-privileged Wear OS platform round-trip probe.
 *
 * A valid pong proves only that the same Sentinel application is reachable over Data Layer. It
 * deliberately refreshes the platform observation while leaving WEARABLE_RUNTIME locked on
 * identity binding. Cryptographic session activation remains the only path to CONNECTED.
 */
class SentinelWearListenerService : WearableListenerService() {
    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path != PLATFORM_PONG_PATH) return
        val payload = messageEvent.data
        if (payload.size != WearOsPlatformProbeRegistry.NONCE_BYTES) return
        if (!WearOsPlatformProbeRegistry.acceptPong(messageEvent.sourceNodeId, payload)) return

        WearableRuntimeCoordinator().observePlatform(
            WearableAdapterObservation(
                stableIdHint = WearOsDataLayerAdapter.pseudonymousStableId(messageEvent.sourceNodeId),
                displayName = null,
                transport = WearableTransport.WEAR_OS,
                platformLinkState = WearablePlatformLinkState.COMPANION_ASSOCIATED,
                availability = WearableAdapterAvailability.AVAILABLE,
                observedCapabilities = setOf(WearableCapability.CONNECTION_STATE)
            )
        )
    }

    companion object {
        const val PLATFORM_PONG_PATH = "/sentinel/v1/platform/pong"
    }
}
