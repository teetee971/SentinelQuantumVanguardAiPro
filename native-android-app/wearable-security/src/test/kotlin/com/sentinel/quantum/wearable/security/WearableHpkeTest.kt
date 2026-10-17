package com.sentinel.quantum.wearable.security

import java.security.GeneralSecurityException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WearableHpkeTest {
    @Test
    fun encryptDecryptRoundTripUsesRequiredHpkeProfile() {
        val recipient = WearableHpkeKeyPair.generate()
        val sender = WearableHpkeSender.fromPublicKey(recipient.publicKey)
        val plaintext = "sentinel-secure-message".toByteArray()
        val context = "sentinel-wearable-channel-v2".toByteArray()

        val ciphertext = sender.encrypt(plaintext, context)

        assertNotEquals(plaintext.toList(), ciphertext.toList())
        assertArrayEquals(plaintext, recipient.decrypt(ciphertext, context))
        assertEqualsRequiredProfile(recipient.profileName)
    }

    @Test
    fun wrongRecipientKeyFails() {
        val recipient = WearableHpkeKeyPair.generate()
        val otherRecipient = WearableHpkeKeyPair.generate()
        val sender = WearableHpkeSender.fromPublicKey(recipient.publicKey)
        val context = "ctx".toByteArray()
        val ciphertext = sender.encrypt("secret".toByteArray(), context)

        assertThrows(GeneralSecurityException::class.java) {
            otherRecipient.decrypt(ciphertext, context)
        }
    }

    @Test
    fun wrongContextInfoFails() {
        val recipient = WearableHpkeKeyPair.generate()
        val sender = WearableHpkeSender.fromPublicKey(recipient.publicKey)
        val ciphertext = sender.encrypt("secret".toByteArray(), "ctx-a".toByteArray())

        assertThrows(GeneralSecurityException::class.java) {
            recipient.decrypt(ciphertext, "ctx-b".toByteArray())
        }
    }

    @Test
    fun oversizedPublicKeyOrCiphertextIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            WearableHpkePublicKey.fromEncoded(ByteArray(WearableHpkeLimits.MAX_PUBLIC_KEY_BYTES + 1))
        }

        val recipient = WearableHpkeKeyPair.generate()
        assertThrows(IllegalArgumentException::class.java) {
            recipient.decrypt(ByteArray(WearableHpkeLimits.MAX_CIPHERTEXT_BYTES + 1), byteArrayOf())
        }
    }

    @Test
    fun privateKeyMaterialIsNotExposedByPublicApi() {
        val forbidden = WearableHpkeKeyPair::class.java.methods
            .map { it.name.lowercase() }
            .filter { it.contains("private") || it.contains("secret") || it.contains("serialize") }

        assertFalse("public API must not expose private/secret serialization: $forbidden", forbidden.isNotEmpty())
    }

    private fun assertEqualsRequiredProfile(actual: String) {
        org.junit.Assert.assertEquals(
            "DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_256_GCM",
            actual
        )
    }
}
