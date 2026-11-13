package com.sentinel.quantum.security

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MmsBoundedImageReaderTest {
    @Test fun acceptsEmptyAndExactBoundaryOnlyAsDefined() {
        assertNull(MmsBoundedImageReader.read(ByteArrayInputStream(ByteArray(0))))
        assertArrayEquals(
            byteArrayOf(1, 2, 3),
            MmsBoundedImageReader.read(ByteArrayInputStream(byteArrayOf(1, 2, 3)), 3)
        )
    }

    @Test fun rejectsBytesBeyondTheConfiguredLimit() {
        assertNull(MmsBoundedImageReader.read(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)), 3))
    }

    @Test fun rejectsAReadThatMakesNoProgress() {
        assertNull(MmsBoundedImageReader.read(object : InputStream() {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int = 0
            override fun read(): Int = 0
        }))
    }

    @Test fun rejectsExceptionsAndPermissionRevocation() {
        assertNull(MmsBoundedImageReader.read(object : InputStream() {
            override fun read(): Int = throw IOException("provider failed")
        }))
        assertNull(
            MmsBoundedImageReader.read(ByteArrayInputStream(byteArrayOf(1))) { false }
        )
    }
}
