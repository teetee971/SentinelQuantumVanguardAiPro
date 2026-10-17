package com.sentinel.quantum.wear

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WearableSecureIdentityStoreTest {
    private class MemoryBackend : WearableIdentityBackend {
        private var pair: KeyPair? = null

        override fun loadOrCreate(): WearableSigningIdentity {
            val current = pair ?: KeyPairGenerator.getInstance("Ed25519")
                .generateKeyPair()
                .also { pair = it }
            return object : WearableSigningIdentity {
                override val publicKeyX509: ByteArray
                    get() = current.public.encoded.copyOf()

                override fun sign(message: ByteArray): ByteArray {
                    val signer = Signature.getInstance("Ed25519")
                    signer.initSign(current.private)
                    signer.update(message)
                    return signer.sign()
                }
            }
        }

        override fun reset() {
            pair = null
        }
    }

    @Test
    fun stableIdentityAcrossStoreReload() {
        val backend = MemoryBackend()
        val first = WearableSecureIdentityStore(backend).snapshot()
        val second = WearableSecureIdentityStore(backend).snapshot()

        assertArrayEquals(first.publicKeyX509, second.publicKeyX509)
        assertEquals(first.fingerprintSha256, second.fingerprintSha256)
    }

    @Test
    fun signaturesVerifyAgainstPublishedIdentity() {
        val store = WearableSecureIdentityStore(MemoryBackend())
        val identity = store.snapshot()
        val message = "sentinel-handshake-v2".toByteArray()
        val signature = store.sign(message)
        val publicKey = java.security.KeyFactory.getInstance("Ed25519")
            .generatePublic(java.security.spec.X509EncodedKeySpec(identity.publicKeyX509))
        val verifier = Signature.getInstance("Ed25519")
        verifier.initVerify(publicKey)
        verifier.update(message)

        assertTrue(verifier.verify(signature))
    }

    @Test
    fun resetChangesIdentityAndRequiresRepairing() {
        val backend = MemoryBackend()
        val store = WearableSecureIdentityStore(backend)
        val before = store.snapshot().fingerprintSha256

        store.reset()
        val after = WearableSecureIdentityStore(backend).snapshot().fingerprintSha256

        assertNotEquals(before, after)
    }

    @Test
    fun publicStoreApiDoesNotExposePrivateKeyMaterial() {
        val forbidden = WearableSecureIdentityStore::class.java.methods
            .map { it.name.lowercase() }
            .filter { it.contains("private") || it.contains("pkcs8") || it.contains("secret") }
        assertFalse("identity store must not export private key bytes: $forbidden", forbidden.isNotEmpty())
    }

    @Test
    fun fingerprintIsLowercaseSha256OfPublicKey() {
        val store = WearableSecureIdentityStore(MemoryBackend())
        val snapshot = store.snapshot()
        val expected = MessageDigest.getInstance("SHA-256")
            .digest(snapshot.publicKeyX509)
            .joinToString("") { "%02x".format(it) }

        assertEquals(expected, snapshot.fingerprintSha256)
        assertTrue(snapshot.fingerprintSha256.matches(Regex("^[0-9a-f]{64}$")))
    }
}
