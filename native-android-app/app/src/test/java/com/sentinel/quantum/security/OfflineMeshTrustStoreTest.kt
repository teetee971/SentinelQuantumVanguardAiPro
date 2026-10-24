package com.sentinel.quantum.security

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineMeshTrustStoreTest {
    @Test
    fun newPeerIsUnknownUntilExplicitApproval() {
        val store = store()
        val key = ecKeyPair()

        assertEquals(OfflineMeshTrustState.UNKNOWN, store.observe(key.public.encoded, 10L).state)
        assertFalse(store.isTrusted(key.public.encoded))
        assertEquals(
            OfflineMeshTrustState.PENDING_APPROVAL,
            store.requestApproval(key.public.encoded, 11L).state
        )
        assertFalse(store.isTrusted(key.public.encoded))
    }

    @Test
    fun approvalPersistsAcrossStoreRecreation() {
        val backend = MemoryBackend()
        val key = ecKeyPair()
        val first = store(backend)
        val pending = first.requestApproval(key.public.encoded, 20L)
        first.approve(pending.stableId, 21L)

        val restarted = store(backend)
        assertTrue(restarted.isTrusted(key.public.encoded))
        assertEquals(OfflineMeshTrustState.TRUSTED, restarted.require(key.public.encoded).state)
    }

    @Test
    fun revocationRemovesAuthorizationAndSurvivesRestart() {
        val backend = MemoryBackend()
        val key = ecKeyPair()
        val first = store(backend)
        val record = first.requestApproval(key.public.encoded, 30L)
        first.approve(record.stableId, 31L)
        first.revoke(record.stableId, 32L)

        val restarted = store(backend)
        assertFalse(restarted.isTrusted(key.public.encoded))
        assertEquals(OfflineMeshTrustState.REVOKED, restarted.require(key.public.encoded).state)
    }

    @Test
    fun oldKeyIsRejectedAfterExplicitRotation() {
        val backend = MemoryBackend()
        val oldKey = ecKeyPair()
        val newKey = ecKeyPair()
        val store = store(backend)
        val record = store.requestApproval(oldKey.public.encoded, 40L)
        store.approve(record.stableId, 41L)

        val proof = signRotation(oldKey, newKey.public.encoded)
        val pending = store.proposeRotation(record.stableId, newKey.public.encoded, proof, 42L)
        // The old key remains trusted until the operator explicitly accepts the
        // proposed replacement; the pending key itself is never trusted.
        assertEquals(OfflineMeshTrustState.TRUSTED, pending.state)
        assertTrue(store.isTrusted(oldKey.public.encoded))
        assertFalse(store.isTrusted(newKey.public.encoded))

        store.acceptRotation(record.stableId, newKey.public.encoded, 43L)
        assertFalse(store.isTrusted(oldKey.public.encoded))
        assertTrue(store.isTrusted(newKey.public.encoded))
    }

    @Test
    fun invalidRotationProofDoesNotCreatePendingTrust() {
        val oldKey = ecKeyPair()
        val newKey = ecKeyPair()
        val store = store()
        val record = store.requestApproval(oldKey.public.encoded, 50L)
        store.approve(record.stableId, 51L)

        assertThrows(OfflineMeshTrustStore.PolicyException::class.java) {
            store.proposeRotation(record.stableId, newKey.public.encoded, byteArrayOf(1, 2), 52L)
        }
        assertFalse(store.isTrusted(newKey.public.encoded))
    }

    @Test
    fun revokedIdentityCannotBeReapprovedWithoutExplicitReset() {
        val store = store()
        val key = ecKeyPair()
        val record = store.requestApproval(key.public.encoded, 60L)
        store.approve(record.stableId, 61L)
        store.revoke(record.stableId, 62L)

        assertThrows(OfflineMeshTrustStore.PolicyException::class.java) {
            store.approve(record.stableId, 63L)
        }
        assertFalse(store.isTrusted(key.public.encoded))
    }

    @Test
    fun failedWriteLeavesPreviousAuthorizationAndFailsClosed() {
        val backend = MemoryBackend().also { it.failWrites = true }
        val store = store(backend)
        val key = ecKeyPair()

        assertThrows(OfflineMeshTrustStore.StorageException::class.java) {
            store.requestApproval(key.public.encoded, 70L)
        }
        assertFalse(store.isTrusted(key.public.encoded))
    }

    @Test
    fun corruptedStorageIsNotTrusted() {
        val backend = MemoryBackend().also {
            it.payload = "corrupted"
            it.mac = byteArrayOf(9)
        }
        val store = store(backend)
        assertEquals(OfflineMeshTrustState.ERROR, store.observe(ecKeyPair().public.encoded, 80L).state)
        assertFalse(store.isTrusted(ecKeyPair().public.encoded))
    }

    @Test
    fun concurrentApprovalRequestsKeepOneStableRecord() {
        val store = store()
        val key = ecKeyPair()
        val results = (1..16).map { Thread { store.requestApproval(key.public.encoded, it.toLong()) } }
        results.forEach(Thread::start)
        results.forEach(Thread::join)

        val records = store.records()
        assertEquals(1, records.size)
        assertNotNull(records.single().stableId)
    }

    private fun store(backend: MemoryBackend = MemoryBackend()) =
        OfflineMeshTrustStore(backend, HmacMac())

    private class MemoryBackend : OfflineMeshTrustBackend {
        var payload: String? = null
        var mac: ByteArray? = null
        var failWrites = false

        override fun read(): OfflineMeshTrustBackend.Entry? =
            payload?.let { OfflineMeshTrustBackend.Entry(it, mac?.copyOf() ?: byteArrayOf()) }

        override fun write(payload: String, mac: ByteArray): Boolean {
            if (failWrites) return false
            this.payload = payload
            this.mac = mac.copyOf()
            return true
        }
    }

    private class HmacMac : OfflineMeshTrustMac {
        override fun mac(payload: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(ByteArray(32) { 7 }, "HmacSHA256"))
            doFinal(payload)
        }
    }

    private fun signRotation(key: KeyPair, newPublicKey: ByteArray): ByteArray =
        Signature.getInstance("SHA256withECDSA").run {
            initSign(key.private)
            update(OfflineMeshTrustStore.rotationMessage(newPublicKey))
            sign()
        }

    private fun ecKeyPair(): KeyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()
}
