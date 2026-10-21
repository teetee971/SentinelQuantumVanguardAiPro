package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class TemporaryApkInventoryTest {
    @Test fun onlyOldMatchingFilesAreListedWithoutModification() {
        val dir = Files.createTempDirectory("sentinel-inventory-test").toFile()
        try {
            val old = dir.resolve("sentinel_apk_inspection_old.apk").apply {
                writeText("old")
                setLastModified(1_000L)
            }
            dir.resolve("sentinel_apk_inspection_recent.apk").apply {
                writeText("recent")
                setLastModified(19_000L)
            }
            dir.resolve("unrelated.apk").apply {
                writeText("unrelated")
                setLastModified(1_000L)
            }
            val list = TemporaryApkInventory.candidates(dir, 20_000L, 5_000L)
            assertEquals(listOf(old.name), list.map { it.name })
            assertTrue(old.exists())
            assertEquals("old", old.readText())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun unknownAndFutureTimestampsAreNotMarkedAbandoned() {
        val dir = Files.createTempDirectory("sentinel-inventory-future").toFile()
        try {
            dir.resolve("sentinel_apk_inspection_future.apk").apply {
                writeText("future")
                setLastModified(30_000L)
            }
            assertTrue(TemporaryApkInventory.candidates(dir, 20_000L, 5_000L).isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun activeFileIsExcludedEvenWhenOld() {
        val dir = Files.createTempDirectory("sentinel-inventory-active").toFile()
        try {
            withTemporaryApk(dir) { current ->
                current.writeText("busy")
                current.setLastModified(1_000L)
                val candidates = TemporaryApkInventory.candidates(dir, 20_000L, 5_000L)
                assertTrue(candidates.isEmpty())
                assertTrue(current.exists())
            }
            assertTrue(dir.listFiles().orEmpty().isEmpty())
        } finally { dir.deleteRecursively() }
    }

    @Test fun missingFolderReturnsNoCandidates() {
        val dir = Files.createTempDirectory("sentinel-inventory-absent").toFile()
        dir.deleteRecursively()
        assertTrue(TemporaryApkInventory.candidates(dir, 20_000L, 5_000L).isEmpty())
    }
}
