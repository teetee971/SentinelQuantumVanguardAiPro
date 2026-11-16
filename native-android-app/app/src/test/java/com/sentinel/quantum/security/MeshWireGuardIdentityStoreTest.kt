package com.sentinel.quantum.security

import com.wireguard.crypto.KeyPair
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshWireGuardIdentityStoreTest {

    @Test
    fun wireGuardGeneratedKeysAreAcceptedAndFingerprintIsStable() {
        val pair = KeyPair()
        val publicKey = pair.publicKey.toBase64()
        val privateKey = pair.privateKey.toBase64()

        assertTrue(MeshWireGuardIdentityStore.validWireGuardKey(publicKey))
        assertTrue(MeshWireGuardIdentityStore.validWireGuardKey(privateKey))

        val fingerprint = MeshWireGuardIdentityStore.fingerprint(publicKey)
        assertTrue(fingerprint.matches(Regex("[a-f0-9]{64}")))
        assertEquals(fingerprint, MeshWireGuardIdentityStore.fingerprint(publicKey))
    }

    @Test
    fun malformedKeysAreRejected() {
        assertFalse(MeshWireGuardIdentityStore.validWireGuardKey(""))
        assertFalse(MeshWireGuardIdentityStore.validWireGuardKey("A".repeat(44)))
        assertFalse(MeshWireGuardIdentityStore.validWireGuardKey("not-base64"))
    }
}
