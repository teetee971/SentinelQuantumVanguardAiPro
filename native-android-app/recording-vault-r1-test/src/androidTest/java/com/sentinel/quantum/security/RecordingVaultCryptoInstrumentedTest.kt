package com.sentinel.quantum.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecordingVaultCryptoInstrumentedTest {

    @Test
    fun encryptDecryptRoundTrip() {
        val original = "Sentinel R1 test".toByteArray()
        val encrypted = RecordingVaultCrypto.encrypt(original)
        assertFalse(original.contentEquals(encrypted))
        assertArrayEquals(
            original,
            RecordingVaultCrypto.decrypt(encrypted)
        )
    }

    @Test
    fun modifiedCiphertextIsRejected() {
        val encrypted = RecordingVaultCrypto.encrypt(
            "Confidential".toByteArray()
        )
        encrypted[encrypted.lastIndex] =
            (encrypted.last().toInt() xor 1).toByte()

        assertThrows(Exception::class.java) {
            RecordingVaultCrypto.decrypt(encrypted)
        }
    }

    @Test
    fun invalidVersionIsRejected() {
        val encrypted = RecordingVaultCrypto.encrypt(
            "Version test".toByteArray()
        )
        encrypted[0] = 99.toByte()

        assertThrows(IllegalArgumentException::class.java) {
            RecordingVaultCrypto.decrypt(encrypted)
        }
    }

    @Test
    fun encryptionUsesDifferentIVs() {
        val data = "Same plaintext".toByteArray()
        val first = RecordingVaultCrypto.encrypt(data)
        val second = RecordingVaultCrypto.encrypt(data)

        assertFalse(
            first.copyOfRange(1, 13)
                .contentEquals(second.copyOfRange(1, 13))
        )
    }

    @Test
    fun emptyPlaintextIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            RecordingVaultCrypto.encrypt(byteArrayOf())
        }
    }
}
