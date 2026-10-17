package com.sentinel.quantum.wear

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService

/**
 * Minimal Wear OS companion boundary.
 *
 * This probe proves only that a same-package/same-signature Sentinel app can exchange a bounded
 * Data Layer message. It is deliberately not the Sentinel cryptographic handshake and grants no
 * privileged capability. The phone must still complete the existing authenticated wearable
 * handshake/session policy before WEARABLE_RUNTIME may become CONNECTED.
 */
class SentinelWearListenerService : WearableListenerService() {
    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path != PLATFORM_PING_PATH) return
        val payload = messageEvent.data
        if (payload.isEmpty() || payload.size > MAX_PROBE_BYTES) return

        Wearable.getMessageClient(this).sendMessage(
            messageEvent.sourceNodeId,
            PLATFORM_PONG_PATH,
            payload
        )
    }

    companion object {
        const val PLATFORM_PING_PATH = "/sentinel/v1/platform/ping"
        const val PLATFORM_PONG_PATH = "/sentinel/v1/platform/pong"
        const val MAX_PROBE_BYTES = 64
    }
}
