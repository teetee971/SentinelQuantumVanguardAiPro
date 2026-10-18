package com.sentinel.quantum.security

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.WearableListenerService
import com.sentinel.quantum.wearable.contract.WearableProtocolV2

/**
 * Phone-side Data Layer receiver.
 *
 * Platform pong remains a non-privileged reachability proof. Secure v2 paths are only decoded and
 * delegated to the current secure-session inbox; this transport boundary never publishes product
 * truth or manufactures channel authentication.
 */
class SentinelWearListenerService : WearableListenerService() {
    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (WearableProtocolV2.isSecurePath(messageEvent.path)) {
            val frame = WearableProtocolV2.decodeFrame(messageEvent.data) ?: return
            WearableSecureTransportInbox.dispatch(messageEvent.sourceNodeId, messageEvent.path, frame)
            return
        }
        if (messageEvent.path != PLATFORM_PONG_PATH) return
        val payload = messageEvent.data
        if (payload.size != WearOsPlatformProbeRegistry.NONCE_BYTES) return
        if (!WearOsPlatformProbeRegistry.acceptPong(messageEvent.sourceNodeId, payload)) return

        SentinelWearableRuntime.observePlatform(
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

    override fun onPeerDisconnected(peer: Node) {
        WearOsPlatformProbeRegistry.cancel(peer.id)
        SentinelWearableSecureHandshakeRuntime.disconnect(peer.id)
    }

    companion object {
        const val PLATFORM_PONG_PATH = "/sentinel/v1/platform/pong"
    }
}
