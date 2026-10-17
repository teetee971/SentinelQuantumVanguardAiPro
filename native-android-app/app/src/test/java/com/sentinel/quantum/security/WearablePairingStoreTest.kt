package com.sentinel.quantum.security

import java.security.KeyPairGenerator
import java.security.MessageDigest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class WearablePairingStoreTest {
    private class MemoryBackend : WearablePairingBackend {
        private val records = mutableMapOf<String, WearableTrustedIdentity>()
        override fun load(stableId: String): WearableTrustedIdentity? = records[stableId]
        override fun save(record: WearableTrustedIdentity) { records[record.stableId] = record }
        override fun remove(stableId: String) { records.remove(stableId) }
    }

    private fun identity(stableId: String = "watch-1"): Pair<ByteArray, String> {
        val publicKey = KeyPairGenerator.getInstance("Ed25519").generateKeyPair().public.encoded
        val fingerprint = MessageDigest.getInstance("SHA-256")
            .digest(publicKey).joinToString("") { "%02x".format(it) }
        return publicKey to fingerprint
    }

    @Test fun confirmedIdentityPersistsAcrossStoreReload() {
        val backend = MemoryBackend()
        val (publicKey, fingerprint) = identity()
        WearablePairingStore(backend).confirm("watch-1", publicKey, fingerprint, 123L)

        val restored = requireNotNull(WearablePairingStore(backend).load("watch-1"))
        assertEquals("watch-1", restored.stableId)
        assertEquals(fingerprint, restored.keyFingerprintSha256)
        assertArrayEquals(publicKey, restored.publicKeyX509)
        assertEquals(123L, restored.confirmedAtMs)
    }

    @Test fun mismatchedFingerprintCannotBePersisted() {
        val (publicKey, _) = identity()
        assertThrows(IllegalArgumentException::class.java) {
            WearablePairingStore(MemoryBackend()).confirm(
                "watch-1", publicKey, "a".repeat(64), 123L
            )
        }
    }

    @Test fun existingIdentityCannotBeSilentlyReplaced() {
        val backend = MemoryBackend()
        val store = WearablePairingStore(backend)
        val (firstKey, firstFingerprint) = identity()
        val (secondKey, secondFingerprint) = identity()
        store.confirm("watch-1", firstKey, firstFingerprint, 123L)

        assertThrows(IllegalStateException::class.java) {
            store.confirm("watch-1", secondKey, secondFingerprint, 124L)
        }
        assertEquals(firstFingerprint, store.load("watch-1")!!.keyFingerprintSha256)
    }

    @Test fun removalRequiresExplicitCall() {
        val backend = MemoryBackend()
        val store = WearablePairingStore(backend)
        val (publicKey, fingerprint) = identity()
        store.confirm("watch-1", publicKey, fingerprint, 123L)
        store.remove("watch-1")
        assertNull(store.load("watch-1"))
    }

    @Test fun failedRemovalCannotBeReportedAsSuccessful() {
        val (publicKey, fingerprint) = identity()
        var record: WearableTrustedIdentity? = null
        val backend = object : WearablePairingBackend {
            override fun load(stableId: String): WearableTrustedIdentity? = record
            override fun save(value: WearableTrustedIdentity) { record = value }
            override fun remove(stableId: String) = Unit
        }
        val store = WearablePairingStore(backend)
        store.confirm("watch-1", publicKey, fingerprint, 123L)

        assertThrows(IllegalStateException::class.java) {
            store.remove("watch-1")
        }
        assertEquals(fingerprint, store.load("watch-1")!!.keyFingerprintSha256)
    }
}
