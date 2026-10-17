package com.sentinel.quantum.wear

import com.sentinel.quantum.wearable.contract.WearableProtocolV2

internal class WearableWatchLivenessFrameHandler(
    private val registry: WearableWatchSecureSessionRegistry,
    private val sender: WearableSecureHandshakeSender,
    private val clockMs: () -> Long = System::currentTimeMillis,
    private val maxAgeMs: Long = DEFAULT_MAX_AGE_MS,
    private val maxFutureSkewMs: Long = DEFAULT_MAX_FUTURE_SKEW_MS
) : WearableSecureFrameHandler {
    init {
        require(maxAgeMs in 1_000L..300_000L)
        require(maxFutureSkewMs in 0L..30_000L)
    }

    override fun onFrame(sourceNodeId: String, path: String, frame: WearableProtocolV2.Frame) {
        if (path != WearableProtocolV2.LIVENESS_PATH) return
        val resolved = registry.resolveNode(sourceNodeId) ?: return
        val inboundContext = WearableProtocolV2.livenessContextInfo(
            sessionId = resolved.sessionId,
            sender = WearableProtocolV2.PeerRole.PHONE,
            recipient = WearableProtocolV2.PeerRole.WEARABLE
        )
        val plaintext = try {
            resolved.session.decryptFromPhone(frame.payload, inboundContext)
        } catch (_: Exception) {
            return
        }
        val liveness = WearableProtocolV2.decodeLivenessPlaintext(plaintext) ?: return
        if (liveness.sessionId != resolved.sessionId) return
        val now = clockMs()
        if (now < 0) return
        if (liveness.sentAtMs > now && liveness.sentAtMs - now > maxFutureSkewMs) return
        if (now >= liveness.sentAtMs && now - liveness.sentAtMs > maxAgeMs) return
        if (!registry.acceptLivenessSequence(sourceNodeId, resolved.sessionId, liveness.sequenceNumber)) return

        val outboundContext = WearableProtocolV2.livenessContextInfo(
            sessionId = resolved.sessionId,
            sender = WearableProtocolV2.PeerRole.WEARABLE,
            recipient = WearableProtocolV2.PeerRole.PHONE
        )
        val ciphertext = try {
            resolved.session.encryptForPhone(plaintext, outboundContext)
        } catch (_: Exception) {
            return
        }
        val encoded = try {
            WearableProtocolV2.encodeFrame(ciphertext)
        } catch (_: IllegalArgumentException) {
            return
        }
        sender.send(sourceNodeId, WearableProtocolV2.LIVENESS_PATH, encoded) { }
    }

    companion object {
        private const val DEFAULT_MAX_AGE_MS = 30_000L
        private const val DEFAULT_MAX_FUTURE_SKEW_MS = 5_000L
    }
}
