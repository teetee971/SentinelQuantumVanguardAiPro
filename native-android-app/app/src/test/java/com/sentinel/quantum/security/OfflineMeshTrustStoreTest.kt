package com.sentinel.quantum.security

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
        val keyRing = KeyRing()
        val key = ecKeyPair()
        val first = store(backend, keyRing)
        val pending = first.requestApproval(key.public.encoded, 20L)
        first.approve(pending.stableId, 21L)

        val restarted = store(backend, keyRing)
        assertTrue(restarted.isTrusted(key.public.encoded))
        assertEquals(OfflineMeshTrustState.TRUSTED, restarted.require(key.public.encoded).state)
    }

    @Test
    fun revocationRemovesAuthorizationAndSurvivesRestart() {
        val backend = MemoryBackend()
        val keyRing = KeyRing()
        val key = ecKeyPair()
        val first = store(backend, keyRing)
        val record = first.requestApproval(key.public.encoded, 30L)
        first.approve(record.stableId, 31L)
        first.revoke(record.stableId, 32L)

        val restarted = store(backend, keyRing)
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
        val store = store(backend, KeyRing())
        val key = ecKeyPair()

        assertThrows(OfflineMeshTrustStore.StorageException::class.java) {
            store.requestApproval(key.public.encoded, 70L)
        }
        assertFalse(store.isTrusted(key.public.encoded))
    }

    @Test
    fun crashAfterRetiringOldKeyCannotRestoreTheOldTrustedState() {
        val backend = MemoryBackend()
        val keyRing = KeyRing()
        val store = store(backend, keyRing)
        val key = ecKeyPair()
        val pending = store.requestApproval(key.public.encoded, 90L)
        store.approve(pending.stableId, 91L)
        keyRing.throwAfterDelete = true

        assertThrows(OfflineMeshTrustStore.StorageException::class.java) {
            store.revoke(pending.stableId, 92L)
        }
        assertFalse(OfflineMeshTrustStore(backend, keyRing).isTrusted(key.public.encoded))
        assertEquals(OfflineMeshTrustState.ERROR, store.observe(key.public.encoded, 93L).state)
    }

    @Test
    fun crashBeforeRetiringOldKeyCannotRestoreTheOldTrustedState() {
        val backend = MemoryBackend()
        val keyRing = KeyRing()
        val store = store(backend, keyRing)
        val key = ecKeyPair()
        val pending = store.requestApproval(key.public.encoded, 94L)
        store.approve(pending.stableId, 95L)
        keyRing.throwBeforeDelete = true

        assertThrows(OfflineMeshTrustStore.StorageException::class.java) {
            store.revoke(pending.stableId, 96L)
        }
        assertFalse(OfflineMeshTrustStore(backend, keyRing).isTrusted(key.public.encoded))
        assertEquals(OfflineMeshTrustState.ERROR, store.observe(key.public.encoded, 97L).state)
    }

    @Test
    fun restoringAnAuthenticatedOldFileAfterRevocationFailsClosed() {
        val backend = MemoryBackend()
        val keyRing = KeyRing()
        val store = store(backend, keyRing)
        val key = ecKeyPair()
        val pending = store.requestApproval(key.public.encoded, 100L)
        store.approve(pending.stableId, 101L)
        val oldEntry = requireNotNull(backend.read())
        store.revoke(pending.stableId, 102L)
        backend.entry = oldEntry

        assertFalse(OfflineMeshTrustStore(backend, keyRing).isTrusted(key.public.encoded))
        assertEquals(OfflineMeshTrustState.ERROR, store.observe(key.public.encoded, 103L).state)
    }

    @Test
    fun failedPublicationAfterOldKeyDeletionRequiresExplicitRecovery() {
        val backend = MemoryBackend()
        val keyRing = KeyRing()
        val store = store(backend, keyRing)
        val key = ecKeyPair()
        val pending = store.requestApproval(key.public.encoded, 110L)
        store.approve(pending.stableId, 111L)
        backend.failWrites = true

        assertThrows(OfflineMeshTrustStore.StorageException::class.java) {
            store.revoke(pending.stableId, 112L)
        }
        assertFalse(store.isTrusted(key.public.encoded))
        assertThrows(OfflineMeshTrustStore.StorageException::class.java) {
            store.requestApproval(key.public.encoded, 113L)
        }
    }

    @Test
    fun keyCreationFailureAfterTransitionMarkFailsClosed() {
        val backend = MemoryBackend()
        val keyRing = KeyRing()
        val store = store(backend, keyRing)
        val key = ecKeyPair()
        val pending = store.requestApproval(key.public.encoded, 120L)
        store.approve(pending.stableId, 121L)
        keyRing.failCreate = true

        assertThrows(OfflineMeshTrustStore.StorageException::class.java) {
            store.revoke(pending.stableId, 122L)
        }
        assertFalse(store.isTrusted(key.public.encoded))
        assertEquals(OfflineMeshTrustState.ERROR, store.observe(key.public.encoded, 123L).state)
    }

    @Test
    fun corruptedStorageIsNotTrusted() {
        val backend = MemoryBackend().also {
            it.entry = OfflineMeshTrustBackend.Entry("corrupted", byteArrayOf(9), 1L)
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

    @Test
    fun concurrentMutationsAcrossStoreInstancesPreserveBothPeers() {
        val backend = ConcurrentBackend()
        val keyRing = KeyRing()
        val firstStore = store(backend, keyRing)
        val secondStore = store(backend, keyRing)
        val firstKey = ecKeyPair()
        val secondKey = ecKeyPair()
        val first = Thread { firstStore.requestApproval(firstKey.public.encoded, 130L) }
        val second = Thread { secondStore.requestApproval(secondKey.public.encoded, 131L) }

        first.start()
        assertTrue(backend.firstWriteStarted.await(5, TimeUnit.SECONDS))
        second.start()
        backend.allowFirstWrite.countDown()
        first.join(5_000L)
        second.join(5_000L)

        assertFalse(first.isAlive)
        assertFalse(second.isAlive)
        assertEquals(2, firstStore.records().size)
    }

    private fun store(
        backend: OfflineMeshTrustBackend = MemoryBackend(),
        keyRing: KeyRing = KeyRing()
    ) = OfflineMeshTrustStore(backend, keyRing)

    private class MemoryBackend : OfflineMeshTrustBackend {
        var entry: OfflineMeshTrustBackend.Entry? = null
        var pending: OfflineMeshTrustBackend.Pending? = null
        var failWrites = false

        override fun read(): OfflineMeshTrustBackend.Entry? = entry?.let {
            it.copy(mac = it.mac.copyOf())
        }

        override fun write(payload: String, mac: ByteArray, keyRevision: Long): Boolean {
            if (failWrites) return false
            entry = OfflineMeshTrustBackend.Entry(payload, mac.copyOf(), keyRevision)
            return true
        }

        override fun readPending(): OfflineMeshTrustBackend.Pending? = pending

        override fun writePending(
            fromRevision: Long,
            toRevision: Long,
            mac: ByteArray
        ): Boolean {
            pending = OfflineMeshTrustBackend.Pending(fromRevision, toRevision, mac.copyOf())
            return true
        }

        override fun clearPending(): Boolean {
            pending = null
            return true
        }
    }

    private class ConcurrentBackend : OfflineMeshTrustBackend {
        val firstWriteStarted = CountDownLatch(1)
        val allowFirstWrite = CountDownLatch(1)
        private var entry: OfflineMeshTrustBackend.Entry? = null

        override fun read(): OfflineMeshTrustBackend.Entry? {
            return entry?.copy(mac = entry!!.mac.copyOf())
        }

        override fun write(payload: String, mac: ByteArray, keyRevision: Long): Boolean {
            if (firstWriteStarted.count == 1L) {
                firstWriteStarted.countDown()
                allowFirstWrite.await(5, TimeUnit.SECONDS)
            }
            entry = OfflineMeshTrustBackend.Entry(payload, mac.copyOf(), keyRevision)
            return true
        }

        override fun readPending(): OfflineMeshTrustBackend.Pending? = null

        override fun writePending(fromRevision: Long, toRevision: Long, mac: ByteArray): Boolean = true

        override fun clearPending(): Boolean = true
    }

    private class KeyRing : OfflineMeshTrustKeyRing {
        private val keys = mutableSetOf<Long>()
        var throwBeforeDelete = false
        var throwAfterDelete = false
        var failCreate = false

        override fun createKey(revision: Long): Boolean {
            if (failCreate) return false
            return keys.add(revision) || revision in keys
        }

        override fun deleteKey(revision: Long): Boolean {
            if (throwBeforeDelete) {
                throwBeforeDelete = false
                error("injected crash before key deletion")
            }
            val removed = keys.remove(revision)
            if (throwAfterDelete) {
                throwAfterDelete = false
                error("injected crash after key deletion")
            }
            return removed
        }

        override fun mac(revision: Long, payload: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
            check(revision in keys) { "key unavailable" }
            init(SecretKeySpec(ByteArray(32) { revision.toByte() }, "HmacSHA256"))
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
