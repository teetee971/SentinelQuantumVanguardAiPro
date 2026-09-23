package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class MmsLocalInboxTest {
    @Test fun listsOnlyBoundedPduFilesNewestFirst() {
        val dir = Files.createTempDirectory("mms-index").toFile()
        val older = java.io.File(dir, "older.pdu").apply { writeBytes(byteArrayOf(1)); setLastModified(10) }
        java.io.File(dir, "ignore.txt").writeText("x")
        val newer = java.io.File(dir, "newer.pdu").apply { writeBytes(byteArrayOf(2,3)); setLastModified(20) }
        val items = MmsLocalInbox.list(dir, 50)
        assertEquals(listOf(newer.name, older.name), items.map { it.name })
        assertEquals(2L, items.first().sizeBytes)
        dir.deleteRecursively()
    }

    @Test fun rejectsEmptyAndOversizedPdusAndBoundsLimit() {
        val dir = Files.createTempDirectory("mms-index").toFile()
        java.io.File(dir, "empty.pdu").writeBytes(byteArrayOf())
        java.io.File(dir, "huge.pdu").writeBytes(ByteArray(17 * 1024 * 1024 + 1))
        assertTrue(MmsLocalInbox.list(dir, 500).isEmpty())
        dir.deleteRecursively()
    }
}
