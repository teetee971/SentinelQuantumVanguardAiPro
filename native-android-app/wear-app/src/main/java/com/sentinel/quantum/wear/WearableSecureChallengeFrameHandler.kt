package com.sentinel.quantum.wear

import com.sentinel.quantum.wearable.contract.WearableProtocolV2

internal fun interface WearableSecureHandshakeSender {
    fun send(
        targetNodeId: String,
        path: String,
        payload: ByteArray,
        onSuccess: () -> Unit
    )
}

internal class WearableSecureChallengeFrameHandler(
    private val respond: (WearableHandshakeRequest) -> WearableHandshakeResponderResult,
    private val sender: WearableSecureHandshakeSender,
    private val onSessionReady: (String, String, WearableHpkeDuplexSession) -> Unit
) : WearableSecureFrameHandler {
    override fun onFrame(sourceNodeId: String, path: String, frame: WearableProtocolV2.Frame) {
        if (path != WearableProtocolV2.CHALLENGE_PATH) return
        val challenge = WearableProtocolV2.decodeChallengeFrame(frame) ?: return
        val result = respond(
            WearableHandshakeRequest(
                sessionId = challenge.sessionId,
                capabilities = challenge.capabilities,
                challengeNonce = challenge.challengeNonce,
                issuedAtMs = challenge.issuedAtMs,
                phoneHpkePublicKeyEncoded = challenge.phoneHpkePublicKeyEncoded,
                phoneHpkePublicKeyFingerprintSha256 = challenge.phoneHpkePublicKeyFingerprintSha256
            )
        )
        val response = result.response
        val encoded = WearableProtocolV2.encodeHandshake(
            WearableProtocolV2.HandshakePayload(
                stableId = response.stableId,
                identityPublicKeyX509 = response.identityPublicKeyX509,
                identityKeyFingerprintSha256 = response.identityKeyFingerprintSha256,
                wearableHpkePublicKeyEncoded = response.wearableHpkePublicKeyEncoded,
                wearableHpkePublicKeyFingerprintSha256 = response.wearableHpkePublicKeyFingerprintSha256,
                signedTranscript = response.signedTranscript,
                signature = response.signature
            )
        )
        sender.send(sourceNodeId, WearableProtocolV2.HANDSHAKE_PATH, encoded) {
            onSessionReady(sourceNodeId, challenge.sessionId, result.session)
        }
    }
}
