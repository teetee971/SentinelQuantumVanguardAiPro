package com.sentinel.quantum.security

import com.sentinel.quantum.wearable.contract.WearableProtocolV2
import com.sentinel.quantum.wearable.security.WearableHandshakeCandidate
import com.sentinel.quantum.wearable.security.WearableHandshakeReplayGuard
import com.sentinel.quantum.wearable.security.WearableHpkeKeyPair
import com.sentinel.quantum.wearable.security.WearableHpkePublicKey
import com.sentinel.quantum.wearable.security.WearableHpkeSender
import java.util.UUID

internal interface PhoneWearableHpkeMaterial {
    val publicKeyEncoded: ByteArray
    val fingerprintSha256: String
    fun encryptForWearable(
        wearablePublicKeyEncoded: ByteArray,
        plaintext: ByteArray,
        contextInfo: ByteArray
    ): ByteArray
    fun decryptFromWearable(ciphertext: ByteArray, contextInfo: ByteArray): ByteArray
}

internal fun interface PhoneWearableHpkeFactory {
    fun create(): PhoneWearableHpkeMaterial
}

internal fun interface PhoneWearableHandshakeTransport {
    fun send(nodeId: String, path: String, payload: ByteArray, onFailure: () -> Unit)
}

internal class PhoneWearableSecureHandshakeRuntime(
    pairingStore: WearablePairingStore,
    private val replayGuard: WearableHandshakeReplayGuard,
    private val sessionAuthority: WearableSessionAuthority,
    private val hpkeFactory: PhoneWearableHpkeFactory,
    private val transport: PhoneWearableHandshakeTransport,
    private val activate: (String, WearableSecureHandshakeResult, WearableAdapterObservation, PhoneWearableHpkeMaterial) -> Boolean,
    private val clockMs: () -> Long = System.currentTimeMillis,
    private val sessionIdFactory: () -> String = { UUID.randomUUID().toString() },
    private val maxPendingNodes: Int = DEFAULT_MAX_PENDING_NODES
) {
    data class PendingConfirmation(
        val stableId: String,
        val identityKeyFingerprintSha256: String,
        val sessionId: String,
        val expiresAtMs: Long
    )

    private data class Pending(
        val nodeId: String,
        val observation: WearableAdapterObservation,
        val context: WearableSecureHandshakeContext,
        val material: PhoneWearableHpkeMaterial,
        var handshake: WearableProtocolV2.HandshakePayload? = null,
        var candidate: WearableHandshakeCandidate? = null
    )

    private val coordinator = WearableSecureHandshakeCoordinator.forPairing(
        pairingStore,
        replayGuard,
        sessionAuthority
    )
    private val pendingByNode = LinkedHashMap<String, Pending>()

    init {
        require(maxPendingNodes in 1..64)
    }

    fun start(
        nodeId: String,
        observation: WearableAdapterObservation,
        capabilities: Set<WearableCapability>
    ): Boolean {
        if (nodeId.isBlank() || nodeId.length > MAX_NODE_ID_CHARS) return false
        if (observation.availability != WearableAdapterAvailability.AVAILABLE) return false
        if (capabilities.isEmpty() || capabilities.size > MAX_CAPABILITIES) return false
        val now = clockMs()
        if (now < 0) return false
        val sessionId = sessionIdFactory().trim()
        if (sessionId.isEmpty() || sessionId.length > MAX_SESSION_ID_CHARS) return false

        val material = try {
            hpkeFactory.create()
        } catch (_: Exception) {
            return false
        }
        if (material.publicKeyEncoded.isEmpty() ||
            !material.fingerprintSha256.matches(SHA256_HEX)
        ) return false

        val session = try {
            WearableSession(
                sessionId = sessionId,
                protocolVersion = SECURE_PROTOCOL_VERSION,
                negotiatedCapabilities = capabilities.toSet(),
                establishedAtMs = now
            )
        } catch (_: IllegalArgumentException) {
            return false
        }
        val context = try {
            coordinator.beginFirstPairing(session, material.fingerprintSha256)
        } catch (_: Exception) {
            return false
        }
        val encoded = try {
            WearableProtocolV2.encodeChallenge(
                WearableProtocolV2.ChallengePayload(
                    sessionId = session.sessionId,
                    capabilities = session.negotiatedCapabilities.map { it.name }.toSet(),
                    challengeNonce = context.challenge.nonce,
                    issuedAtMs = context.challenge.issuedAtMs,
                    phoneHpkePublicKeyEncoded = material.publicKeyEncoded.copyOf(),
                    phoneHpkePublicKeyFingerprintSha256 = material.fingerprintSha256
                )
            )
        } catch (_: Exception) {
            replayGuard.revoke(context.challenge.nonce)
            return false
        }

        synchronized(this) {
            purgeExpired(now)
            if (nodeId !in pendingByNode && pendingByNode.size >= maxPendingNodes) {
                replayGuard.revoke(context.challenge.nonce)
                return false
            }
            pendingByNode.remove(nodeId)?.let { replayGuard.revoke(it.context.challenge.nonce) }
            pendingByNode[nodeId] = Pending(nodeId, observation, context, material)
        }
        val attemptId = context.attempt.attemptId
        return try {
            transport.send(nodeId, WearableProtocolV2.CHALLENGE_PATH, encoded) {
                removePendingAndRevoke(nodeId, attemptId)
            }
            true
        } catch (_: Exception) {
            removePendingAndRevoke(nodeId, attemptId)
            false
        }
    }

    fun handleHandshake(
        nodeId: String,
        frame: WearableProtocolV2.Frame
    ): WearableSecureHandshakeResult {
        val handshake = WearableProtocolV2.decodeHandshakeFrame(frame)
            ?: return rejected(WearableSecureHandshakeRejection.INVALID_SIGNATURE)
        val pending = synchronized(this) {
            purgeExpired(clockMs())
            pendingByNode[nodeId]
        } ?: return rejected(WearableSecureHandshakeRejection.REPLAY_REJECTED)
        val candidate = WearableHandshakeCandidate(
            stableId = handshake.stableId,
            keyFingerprintSha256 = handshake.identityKeyFingerprintSha256,
            sessionId = pending.context.session.sessionId,
            protocolVersion = SECURE_PROTOCOL_VERSION,
            capabilities = pending.context.session.negotiatedCapabilities.map { it.name }.toSet(),
            challengeNonce = pending.context.challenge.nonce,
            issuedAtMs = pending.context.challenge.issuedAtMs,
            signedTranscript = handshake.signedTranscript.copyOf(),
            signature = handshake.signature.copyOf(),
            phoneHpkePublicKeyFingerprintSha256 = pending.material.fingerprintSha256,
            wearableHpkePublicKeyFingerprintSha256 = handshake.wearableHpkePublicKeyFingerprintSha256
        )
        val result = try {
            coordinator.acceptPresentedIdentity(
                context = pending.context,
                candidate = candidate,
                identityPublicKeyX509 = handshake.identityPublicKeyX509,
                wearableHpkePublicKeyEncoded = handshake.wearableHpkePublicKeyEncoded,
                confirmation = null
            )
        } catch (_: Exception) {
            return rejected(WearableSecureHandshakeRejection.TRUST_ANCHOR_MISMATCH)
        }

        if (result.state == WearableSecureHandshakeState.PENDING_CONFIRMATION) {
            synchronized(this) {
                val current = pendingByNode[nodeId]
                if (current === pending) {
                    current.handshake = handshake
                    current.candidate = candidate
                }
            }
        }
        return finalizeIfActive(pending, result)
    }

    @Synchronized
    fun pendingConfirmation(): PendingConfirmation? {
        purgeExpired(clockMs())
        return pendingByNode.values.firstNotNullOfOrNull { pending ->
            val handshake = pending.handshake ?: return@firstNotNullOfOrNull null
            val candidate = pending.candidate ?: return@firstNotNullOfOrNull null
            PendingConfirmation(
                stableId = candidate.stableId,
                identityKeyFingerprintSha256 = handshake.identityKeyFingerprintSha256,
                sessionId = pending.context.session.sessionId,
                expiresAtMs = pending.context.challenge.expiresAtMs
            )
        }
    }

    fun confirm(stableId: String, confirmedAtMs: Long): WearableSecureHandshakeResult? {
        val pending = synchronized(this) {
            purgeExpired(clockMs())
            pendingByNode.values.firstOrNull { it.candidate?.stableId == stableId }
        } ?: return null
        val handshake = pending.handshake ?: return null
        val candidate = pending.candidate ?: return null
        val result = try {
            coordinator.acceptPresentedIdentity(
                context = pending.context,
                candidate = candidate,
                identityPublicKeyX509 = handshake.identityPublicKeyX509,
                wearableHpkePublicKeyEncoded = handshake.wearableHpkePublicKeyEncoded,
                confirmation = WearableIdentityPairingConfirmation(
                    stableId = stableId,
                    sessionId = pending.context.session.sessionId,
                    identityKeyFingerprintSha256 = handshake.identityKeyFingerprintSha256,
                    confirmedAtMs = confirmedAtMs
                )
            )
        } catch (_: Exception) {
            return rejected(WearableSecureHandshakeRejection.IDENTITY_CONFIRMATION_MISMATCH)
        }
        return finalizeIfActive(pending, result)
    }

    fun reject(stableId: String): Boolean {
        if (stableId.isBlank()) return false
        synchronized(this) {
            purgeExpired(clockMs())
            val entry = pendingByNode.entries.firstOrNull { it.value.candidate?.stableId == stableId }
                ?: return false
            pendingByNode.remove(entry.key)
            replayGuard.revoke(entry.value.context.challenge.nonce)
            return true
        }
    }

    @Synchronized
    internal fun pendingForNode(nodeId: String): String? {
        purgeExpired(clockMs())
        return pendingByNode[nodeId]?.context?.session?.sessionId
    }

    fun disconnect(nodeId: String) {
        synchronized(this) {
            val pending = pendingByNode.remove(nodeId) ?: return
            replayGuard.revoke(pending.context.challenge.nonce)
        }
    }

    private fun finalizeIfActive(
        pending: Pending,
        result: WearableSecureHandshakeResult
    ): WearableSecureHandshakeResult {
        if (result.state != WearableSecureHandshakeState.ACTIVE) return result
        if (!activate(pending.nodeId, result, pending.observation, pending.material)) {
            result.verifiedProof?.stableId?.let(sessionAuthority::revoke)
            synchronized(this) { pendingByNode.remove(pending.nodeId) }
            return rejected(WearableSecureHandshakeRejection.AUTHORITY_REJECTED)
        }
        synchronized(this) { pendingByNode.remove(pending.nodeId) }
        return result
    }

    private fun removePendingAndRevoke(nodeId: String, attemptId: String) {
        synchronized(this) {
            val current = pendingByNode[nodeId]
            if (current?.context?.attempt?.attemptId != attemptId) return
            pendingByNode.remove(nodeId)
            replayGuard.revoke(current.context.challenge.nonce)
        }
    }

    private fun purgeExpired(nowMs: Long) {
        if (nowMs < 0) {
            pendingByNode.clear()
            replayGuard.revokeAll()
            return
        }
        val expired = pendingByNode.values.filter { nowMs > it.context.challenge.expiresAtMs }
        expired.forEach {
            pendingByNode.remove(it.nodeId)
            replayGuard.revoke(it.context.challenge.nonce)
        }
    }

    private fun rejected(reason: WearableSecureHandshakeRejection) = WearableSecureHandshakeResult(
        state = WearableSecureHandshakeState.REJECTED,
        rejection = reason
    )

    companion object {
        private const val SECURE_PROTOCOL_VERSION = 2
        private const val DEFAULT_MAX_PENDING_NODES = 8
        private const val MAX_NODE_ID_CHARS = 512
        private const val MAX_SESSION_ID_CHARS = 256
        private const val MAX_CAPABILITIES = 32
        private val SHA256_HEX = Regex("^[0-9a-f]{64}$")

        fun tinkHpkeFactory(): PhoneWearableHpkeFactory = PhoneWearableHpkeFactory {
            val pair = WearableHpkeKeyPair.generate()
            object : PhoneWearableHpkeMaterial {
                override val publicKeyEncoded: ByteArray = pair.publicKey.encoded()
                override val fingerprintSha256: String = pair.publicKey.fingerprintSha256

                override fun encryptForWearable(
                    wearablePublicKeyEncoded: ByteArray,
                    plaintext: ByteArray,
                    contextInfo: ByteArray
                ): ByteArray {
                    val wearablePublic = WearableHpkePublicKey.fromEncoded(wearablePublicKeyEncoded)
                    return WearableHpkeSender.fromPublicKey(wearablePublic)
                        .encrypt(plaintext, contextInfo)
                }

                override fun decryptFromWearable(ciphertext: ByteArray, contextInfo: ByteArray): ByteArray =
                    pair.decrypt(ciphertext, contextInfo)
            }
        }
    }
}
