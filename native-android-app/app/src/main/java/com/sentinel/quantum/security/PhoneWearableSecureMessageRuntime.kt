package com.sentinel.quantum.security

import com.sentinel.quantum.wearable.contract.WearableProtocolV2

/**
 * Authenticated inbound message gate. The only negotiated production capability today is
 * CONNECTION_STATE; no capability handler is installed until its business contract is reviewed.
 * Never publish connectivity truth merely because an application ciphertext decrypts.
 */
internal class PhoneWearableSecureMessageRuntime(
    private val authority: WearableSessionAuthority,
    private val livenessActive: (String, String) -> Boolean,
    private val clockMs: () -> Long = System::currentTimeMillis
) {
    private data class Active(
        val nodeId: String,
        val stableId: String,
        val session: WearableSession,
        val channel: WearableSecureChannel
    )

    private var active: Active? = null

    @Synchronized
    fun activate(
        nodeId: String,
        stableId: String,
        session: WearableSession,
        wearablePublicKeyEncoded: ByteArray,
        material: PhoneWearableHpkeMaterial
    ): Boolean {
        if (nodeId.isBlank() || stableId.isBlank() || wearablePublicKeyEncoded.isEmpty()) return false
        // Only publish the new epoch after its authority is established.
        if (authority.activeSession(stableId) != session) return false
        val endpoint = object : WearableSecureCryptoEndpoint {
            override fun encrypt(plaintext: ByteArray, contextInfo: ByteArray): ByteArray =
                material.encryptForWearable(wearablePublicKeyEncoded, plaintext, contextInfo)
            override fun decrypt(ciphertext: ByteArray, contextInfo: ByteArray): ByteArray =
                material.decryptFromWearable(ciphertext, contextInfo)
        }
        active = Active(
            nodeId, stableId, session,
            WearableSecureChannel(session, WearablePeerRole.PHONE, endpoint)
        )
        return true
    }

    @Synchronized
    fun disconnect(nodeId: String) {
        if (active?.nodeId == nodeId) active = null
    }

    @Synchronized
    fun handleFrame(nodeId: String, frame: WearableProtocolV2.Frame): Boolean {
        val current = active ?: return false
        if (current.nodeId != nodeId) return false
        if (authority.activeSession(current.stableId) != current.session) {
            active = null
            return false
        }
        if (!livenessActive(nodeId, current.session.sessionId)) return false
        if (WearableCapability.CONNECTION_STATE !in current.session.negotiatedCapabilities) return false
        // Fixed AAD capability until the protocol negotiates an authenticated capability header.
        val result = try {
            current.channel.open(
                frame.payload,
                expectedCapability = WearableCapability.CONNECTION_STATE,
                senderRole = WearablePeerRole.WEARABLE,
                nowMs = clockMs()
            )
        } catch (_: Exception) {
            return false
        }
        // No business consumer is registered yet. Authenticated frames are consumed by the gate,
        // but must never be interpreted as permission to change product state.
        return result.state == WearableSecureMessageState.ACCEPTED
    }
}
