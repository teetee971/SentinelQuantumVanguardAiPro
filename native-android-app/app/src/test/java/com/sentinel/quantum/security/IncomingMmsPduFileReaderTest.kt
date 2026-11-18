package com.sentinel.quantum.security

import java.io.ByteArrayInputStream
import java.io.File
import java.io.RandomAccessFile
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class IncomingMmsPduFileReaderTest {
    @get:Rule val directory = TemporaryFolder()

    @Test
    fun readsCompleteFilesThroughTheExistingPduLimit() {
        val file = directory.newFile()
        val expected = byteArrayOf(0x01, 0x02, 0x03)
        file.writeBytes(expected)
        assertArrayEquals(expected, IncomingMmsPduFileReader.read(file, expected.size.toLong()))

        val maximum = IncomingMmsPduFileReader.MAX_BYTES
        assertEquals(17L * 1024 * 1024, maximum)
        RandomAccessFile(file, "rw").use { it.setLength(maximum) }
        assertEquals(maximum.toInt(), IncomingMmsPduFileReader.read(file, maximum)?.size)
    }

    @Test
    fun rejectsEmptyOversizedAndTruncatedFilesWithoutChangingThem() {
        val file = directory.newFile()
        assertNull(IncomingMmsPduFileReader.read(file, 0))
        file.writeBytes(byteArrayOf(0x01))
        assertNull(IncomingMmsPduFileReader.read(file, 2))

        val oversized = IncomingMmsPduFileReader.MAX_BYTES + 1
        RandomAccessFile(file, "rw").use { it.setLength(oversized) }
        assertNull(IncomingMmsPduFileReader.read(file, oversized))
        assertEquals(oversized, file.length())
    }

    @Test
    fun rejectsAFileThatGrewAfterItsLengthWasChecked() {
        val file = directory.newFile()
        file.writeBytes(byteArrayOf(0x01, 0x02))
        val measuredLength = file.length()
        file.appendBytes(byteArrayOf(0x03))

        assertNull(IncomingMmsPduFileReader.read(file, measuredLength))
        assertEquals(3L, file.length())
        assertArrayEquals(byteArrayOf(0x01, 0x02, 0x03), IncomingMmsPduFileReader.read(file, 3))
    }

    @Test
    fun streamReadsOnlyMeasuredLengthAndOneByteToDetectGrowth() {
        val stream = ByteArrayInputStream(ByteArray(40))
        assertNull(IncomingMmsPduFileReader.readExact(stream, 3))
        assertEquals(36, stream.available())
    }

    @Test
    fun unavailableFilesFailClosed() {
        val missing = File(directory.root, "missing.pdu")
        assertNull(IncomingMmsPduFileReader.read(missing, 1))
    }
}
