package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import javax.crypto.spec.SecretKeySpec

/**
 * Tests the pure HMAC formatting logic in [computeHmacFingerprint], which is deliberately kept
 * free of any Android Keystore dependency (see [CallNumberFingerprinter]). A plain
 * [SecretKeySpec] stands in for the device-bound key so these tests run on the plain JVM.
 */
class CallNumberFingerprinterTest {
    private val key = SecretKeySpec(ByteArray(32) { it.toByte() }, "HmacSHA256")

    @Test fun fingerprintIsStableForTheSameInputAndKey() {
        val first = computeHmacFingerprint(key, "+33612345678")
        val second = computeHmacFingerprint(key, "+33612345678")
        assertNotNull(first)
        assertEquals(first, second)
    }

    @Test fun fingerprintIsSixtyFourHexCharactersLong() {
        val value = computeHmacFingerprint(key, "+33612345678")
        assertEquals(64, value?.length)
        assertNotNull(value)
        assertEquals(value, value?.lowercase())
        assertTrueAllHex(value!!)
    }

    @Test fun differentInputsProduceDifferentFingerprints() {
        val a = computeHmacFingerprint(key, "+33612345678")
        val b = computeHmacFingerprint(key, "+33698765432")
        assertNotEquals(a, b)
    }

    @Test fun differentKeysProduceDifferentFingerprintsForTheSameInput() {
        val otherKey = SecretKeySpec(ByteArray(32) { (it + 1).toByte() }, "HmacSHA256")
        val a = computeHmacFingerprint(key, "+33612345678")
        val b = computeHmacFingerprint(otherKey, "+33612345678")
        assertNotEquals(a, b)
    }

    private fun assertTrueAllHex(value: String) {
        assert(value.all { it.isDigit() || it in 'a'..'f' }) { "Expected lowercase hex, got: $value" }
    }
}
