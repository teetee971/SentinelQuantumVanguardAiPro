package com.sentinel.quantum.data

import java.io.ByteArrayInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BoundedInputReaderTest {
    @Test
    fun acceptsInputAtLimit() {
        val bytes = byteArrayOf(1, 2, 3, 4)
        assertArrayEquals(bytes, BoundedInputReader.read(ByteArrayInputStream(bytes), 4))
    }

    @Test
    fun rejectsInputAboveLimit() {
        assertNull(BoundedInputReader.read(ByteArrayInputStream(ByteArray(5)), 4))
    }
}
