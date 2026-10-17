package com.sentinel.quantum.security

import com.sentinel.quantum.wearable.contract.WearableProtocolV2
import java.security.MessageDigest
import java.security.SecureRandom

internal fun interface PhoneWearableLivenessTransport {
    fun send(nodeId: String, path: String, payload: ByteArray, onFailure: () -> Unit)
}

internal class PhoneWearableLivenessRuntime(
    private val transport: PhoneWearableLivenessTransport,
    private val authenticatedTraffic: (String, Long) -> Boolean,
    private val revokeAuthenticatedChannel: (Long) -> Unit,
    private val clockMs: () -> Long = System::currentTimeMillis,
    private val random: SecureRandom = SecureRandom(),
    private val proofTtlMs: Long = DEFAULT_PROOF_TTL_MS
) {
    private data class PendingProbe(
        val sequenceNumber: Long,
        val sentAtMs: Long,
        val nonce: ByteArray
    )

    private data class Active(
        val sessionId: String,
        val wearablePublicKeyEncoded: ByteArray,
        val material: PhoneWearableHpkeMaterial,
        var nextSequenceNumber: Long = 0L,
        var pending: PendingProbe? = null
    )

    private val activeByNode = LinkedHashMap<String, Active>()

    init {
        require(proofTtlMs in 1_000L..300_000L)
    }

    /**
     * The phone secure-session runtime is currently single-authority. A newer session therefore
     * atomically supersedes every older Data Layer node instead of allowing stale nodes to revoke
     * or refresh the new authoritative session later.
     */
    @Synchronized
    fun activate(
        nodeId: String,
        sessionId: String,
        wearablePublicKeyEncoded: ByteArray,
        material: PhoneWearableHpkeMaterial
    ): Boolean {
        if (nodeId.isBlank() || sessionId.isBlank()) return false
        if (wearablePublicKeyEncoded.isEmpty() || wearablePublicKeyEncoded.size > MAX_HPKE_PUBLIC_KEY_BYTES) return false
        activeByNode.values.forEach { it.pending?.nonce?.fill(0) }
        activeByNode.clear()
        activeByNode[nodeId] = Active(
            sessionId = sessionId,
            wearablePublicKeyEncoded = wearablePublicKeyEncoded.copyOf(),
            material = material
        )
        return true
    }

    fun sendProbe(nodeId: String): Boolean {
        val now = clockMs()
        if (now < 0) return false
        val prepared = synchronized(this) {
            val active = activeByNode[nodeId] ?: return false
            val existing = active.pending
            if (existing != null) {
                if (now < existing.sentAtMs || now - existing.sentAtMs <= proofTtlMs) return false
                existing.nonce.fill(0)
                active.pending = null
            }
            val sequence = active.nextSequenceNumber
            val next = try {
                Math.addExact(sequence, 1L)
            } catch (_: ArithmeticException) {
                activeByNode.remove(nodeId)
                return false
            }
            val nonce = ByteArray(LIVENESS_NONCE_BYTES).also(random::nextBytes)
            val probe = PendingProbe(sequence, now, nonce)
            val plaintext = WearableProtocolV2.encodeLivenessPlaintext(
                WearableProtocolV2.LivenessPayload(
                    sessionId = active.sessionId,
                    sequenceNumber = sequence,
                    sentAtMs = now,
                    nonce = nonce.copyOf()
                )
            )
            val context = WearableProtocolV2.livenessContextInfo(
                sessionId = active.sessionId,
                sender = WearableProtocolV2.PeerRole.PHONE,
                recipient = WearableProtocolV2.PeerRole.WEARABLE
            )
            val ciphertext = try {
                active.material.encryptForWearable(active.wearablePublicKeyEncoded, plaintext, context)
            } catch (_: Exception) {
                nonce.fill(0)
                return false
            }
            val encoded = try {
                WearableProtocolV2.encodeFrame(ciphertext)
            } catch (_: IllegalArgumentException) {
                nonce.fill(0)
                return false
            }
            active.pending = probe
            active.nextSequenceNumber = next
            Triple(active.sessionId, sequence, encoded)
        }

        return try {
            transport.send(nodeId, WearableProtocolV2.LIVENESS_PATH, prepared.third) {
                revokeFailedProbe(nodeId, prepared.first, prepared.second)
            }
            true
        } catch (_: Exception) {
            revokeFailedProbe(nodeId, prepared.first, prepared.second)
            false
        }
    }

    fun handleResponse(nodeId: String, frame: WearableProtocolV2.Frame): Boolean {
        val snapshot = synchronized(this) {
            val active = activeByNode[nodeId] ?: return false
            val pending = active.pending ?: return false
            Triple(active, pending, active.sessionId)
        }
        val active = snapshot.first
        val pending = snapshot.second
        val sessionId = snapshot.third
        val now = clockMs()
        if (now < pending.sentAtMs || now - pending.sentAtMs > proofTtlMs) {
            clearPending(nodeId, sessionId, pending.sequenceNumber)
            return false
        }
        val context = WearableProtocolV2.livenessContextInfo(
            sessionId = sessionId,
            sender = WearableProtocolV2.PeerRole.WEARABLE,
            recipient = WearableProtocolV2.PeerRole.PHONE
        )
        val plaintext = try {
            active.material.decryptFromWearable(frame.payload, context)
        } catch (_: Exception) {
            return false
        }
        val response = WearableProtocolV2.decodeLivenessPlaintext(plaintext) ?: return false
        if (response.sessionId != sessionId ||
            response.sequenceNumber != pending.sequenceNumber ||
            response.sentAtMs != pending.sentAtMs ||
            !MessageDigest.isEqual(response.nonce, pending.nonce)
        ) return false

        val consumed = synchronized(this) {
            val current = activeByNode[nodeId] ?: return@synchronized false
            if (current !== active || current.sessionId != sessionId) return@synchronized false
            val currentPending = current.pending ?: return@synchronized false
            if (currentPending.sequenceNumber != pending.sequenceNumber ||
                !MessageDigest.isEqual(currentPending.nonce, pending.nonce)
            ) return@synchronized false
            currentPending.nonce.fill(0)
            current.pending = null
            true
        }
        if (!consumed) return false
        val accepted = try {
            authenticatedTraffic(sessionId, now)
        } catch (_: Exception) {
            false
        }
        if (accepted) return true

        // A slow/throwing callback from an old session must not revoke a replacement.
        val removed = synchronized(this) {
            if (activeByNode[nodeId] !== active) false
            else {
                activeByNode.remove(nodeId)
                true
            }
        }
        if (removed) revokeAuthenticatedChannel(now)
        return false
    }

    fun disconnect(nodeId: String) {
        val removed = synchronized(this) { activeByNode.remove(nodeId) } ?: return
        removed.pending?.nonce?.fill(0)
        val now = clockMs()
        revokeAuthenticatedChannel(if (now >= 0) now else 0L)
    }

    @Synchronized
    fun isActive(nodeId: String, sessionId: String): Boolean =
        activeByNode[nodeId]?.sessionId == sessionId

    private fun revokeFailedProbe(nodeId: String, sessionId: String, sequenceNumber: Long) {
        val removed = synchronized(this) {
            val active = activeByNode[nodeId] ?: return
            if (active.sessionId != sessionId) return
            val pending = active.pending ?: return
            if (pending.sequenceNumber != sequenceNumber) return
            pending.nonce.fill(0)
            activeByNode.remove(nodeId)
        }
        removed.pending?.nonce?.fill(0)
        val now = clockMs()
        revokeAuthenticatedChannel(if (now >= 0) now else 0L)
    }

    private fun clearPending(nodeId: String, sessionId: String, sequenceNumber: Long) {
        synchronized(this) {
            val active = activeByNode[nodeId] ?: return
            if (active.sessionId != sessionId) return
            val pending = active.pending ?: return
            if (pending.sequenceNumber != sequenceNumber) return
            pending.nonce.fill(0)
            active.pending = null
        }
    }

    companion object {
        private const val DEFAULT_PROOF_TTL_MS = 30_000L
        private const val MAX_HPKE_PUBLIC_KEY_BYTES = 4 * 1024
        private const val LIVENESS_NONCE_BYTES = 16
    }
}
