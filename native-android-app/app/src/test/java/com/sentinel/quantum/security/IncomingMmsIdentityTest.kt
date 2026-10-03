package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingMmsIdentityTest {
    @Test
    fun identityIsStableAndUsesFullSha256() {
        val pdu = "same-pdu".toByteArray(Charsets.UTF_8)
        val first = IncomingMmsIdentity.sha256Hex(pdu)
        val second = IncomingMmsIdentity.sha256Hex(pdu.copyOf())

        assertEquals(first, second)
        assertTrue(first != null && first.length == 64)
        assertTrue(first!!.all { it in '0'..'9' || it in 'a'..'f' })
        assertEquals("$first.pdu", IncomingMmsIdentity.persistedFileName(first))
        assertEquals("$first.part", IncomingMmsIdentity.partialFileName(first))
    }

    @Test
    fun oneByteDifferenceProducesDifferentIdentity() {
        val first = IncomingMmsIdentity.sha256Hex(byteArrayOf(1, 2, 3, 4))
        val second = IncomingMmsIdentity.sha256Hex(byteArrayOf(1, 2, 3, 5))
        assertNotEquals(first, second)
    }

    @Test
    fun invalidInputsAndDigestNamesFailClosed() {
        assertNull(IncomingMmsIdentity.sha256Hex(ByteArray(0)))
        assertNull(IncomingMmsIdentity.persistedFileName("abc"))
        assertNull(IncomingMmsIdentity.partialFileName("../" + "a".repeat(64)))
    }

    @Test
    fun uppercaseDigestIsNormalizedBeforeFileNaming() {
        val digest = "AB".repeat(32)
        assertEquals("${digest.lowercase()}.pdu", IncomingMmsIdentity.persistedFileName(digest))
        assertEquals("${digest.lowercase()}.part", IncomingMmsIdentity.partialFileName(digest))
    }
}
