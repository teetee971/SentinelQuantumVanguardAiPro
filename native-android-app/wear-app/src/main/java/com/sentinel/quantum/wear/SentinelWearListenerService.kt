package com.sentinel.quantum.wear

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.sentinel.quantum.wearable.contract.WearableProtocolV2

/**
 * Wear OS Data Layer receiver.
 *
 * Platform ping/pong remains a non-privileged reachability probe. Secure v2 frames are decoded and
 * delegated to the current secure-session inbox; this transport service never promotes product
 * truth and never treats Data Layer reachability as Sentinel authentication.
 */
class SentinelWearListenerService : WearableListenerService() {
    private var secureFrameHandler: WearableSecureFrameHandler? = null

    override fun onCreate() {
        super.onCreate()
        val responder = WearableSecureHandshakeResponder(
            WearableSecureIdentityStore.forApplicationContext(applicationContext)
        )
        val messageClient = Wearable.getMessageClient(this)
        val handler = WearableSecureChallengeFrameHandler(
            respond = responder::respond,
            sender = WearableSecureHandshakeSender { targetNodeId, path, payload, onSuccess ->
                messageClient.sendMessage(targetNodeId, path, payload)
                    .addOnSuccessListener { onSuccess() }
            },
            onSessionReady = { sourceNodeId, sessionId, session ->
                SentinelWearSecureSessions.registry.register(sourceNodeId, sessionId, session)
            }
        )
        secureFrameHandler = handler
        WearableSecureTransportInbox.install(handler)
    }

    override fun onDestroy() {
        val handler = secureFrameHandler
        if (handler != null && WearableSecureTransportInbox.clear(handler)) {
            SentinelWearSecureSessions.registry.clear()
        }
        secureFrameHandler = null
        super.onDestroy()
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (WearableProtocolV2.isSecurePath(messageEvent.path)) {
            val frame = WearableProtocolV2.decodeFrame(messageEvent.data) ?: return
            WearableSecureTransportInbox.dispatch(messageEvent.sourceNodeId, messageEvent.path, frame)
            return
        }
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
