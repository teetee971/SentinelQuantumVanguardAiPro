package com.sentinel.quantum.security

import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import org.junit.Assert.*
import org.junit.Test

class MmsProviderStreamTest {
    @Test fun deletionBetweenMetadataAndOpeningFailsClosed() {
        assertNull(MmsBoundedImageReader.readFromProvider({ throw FileNotFoundException("part removed") }, { true }))
    }

    @Test fun permissionRevokedBeforeOpeningDoesNotAccessTheProvider() {
        var opened = false
        assertNull(MmsBoundedImageReader.readFromProvider({ opened = true; ByteArrayInputStream(byteArrayOf(1)) }, { false }))
        assertFalse(opened)
    }

    @Test fun validStreamIsBoundedAndClosedByOneOwner() {
        var closes = 0
        val stream = object : ByteArrayInputStream(byteArrayOf(1, 2, 3)) {
            override fun close() { closes++; super.close() }
        }
        assertArrayEquals(byteArrayOf(1, 2, 3), MmsBoundedImageReader.readFromProvider({ stream }, { true }))
        assertEquals(1, closes)
    }

    @Test fun providerCannotReleaseMoreThanEightMiB() {
        assertNull(MmsBoundedImageReader.readFromProvider(
            { ByteArrayInputStream(ByteArray(8 * 1024 * 1024 + 1)) }, { true }
        ))
    }

    @Test fun permissionRevokedDuringTheReadDiscardsBytesAndClosesTheStream() {
        var permitted = true
        var closed = false
        val stream = object : ByteArrayInputStream(byteArrayOf(1, 2, 3)) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                val count = super.read(buffer, offset, length)
                permitted = false
                return count
            }
            override fun close() { closed = true; super.close() }
        }
        assertNull(MmsBoundedImageReader.readFromProvider({ stream }, { permitted }))
        assertTrue(closed)
    }

    @Test fun revokedPermissionOrProviderRuntimeFailureDoesNotEscape() {
        assertNull(MmsBoundedImageReader.readFromProvider({ throw SecurityException("permission revoked") }, { true }))
        assertNull(MmsBoundedImageReader.readFromProvider({ throw IllegalArgumentException("unsupported URI") }, { true }))
        assertNull(MmsBoundedImageReader.readFromProvider({ null }, { true }))
    }
}
