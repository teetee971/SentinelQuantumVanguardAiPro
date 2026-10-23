package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ApkInspectionSafetyTest {
    private fun archive(vararg names: String): File {
        val file = Files.createTempFile("sentinel-archive-test-", ".apk").toFile()
        ZipOutputStream(file.outputStream()).use { zip ->
            names.forEach { name ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(byteArrayOf(1, 2, 3))
                zip.closeEntry()
            }
        }
        return file
    }

    @Test fun validArchiveWithManifestAccepted() {
        val file = archive("AndroidManifest.xml", "classes.dex")
        try {
            ApkArchiveValidator.validate(file)
        } finally {
            file.delete()
        }
    }

    @Test fun emptyManifestRejected() {
        val file = Files.createTempFile("sentinel-empty-manifest-", ".apk").toFile()
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("classes.dex"))
                zip.write(byteArrayOf(1, 2, 3))
                zip.closeEntry()
            }
            assertThrows(IllegalArgumentException::class.java) {
                ApkArchiveValidator.validate(file)
            }
        } finally {
            file.delete()
        }
    }

    @Test fun missingManifestRejected() {
        val file = archive("classes.dex")
        try {
            assertThrows(IllegalArgumentException::class.java) {
                ApkArchiveValidator.validate(file)
            }
        } finally {
            file.delete()
        }
    }

    @Test fun traversalRejected() {
        val file = archive("AndroidManifest.xml", "../secret.txt")
        try {
            assertThrows(IllegalArgumentException::class.java) {
                ApkArchiveValidator.validate(file)
            }
        } finally {
            file.delete()
        }
    }

    @Test fun emptyFileRejected() {
        val file = Files.createTempFile("sentinel-empty-", ".apk").toFile()
        try {
            assertThrows(IllegalArgumentException::class.java) {
                ApkArchiveValidator.validate(file)
            }
        } finally {
            file.delete()
        }
    }

    @Test fun temporaryFileDeletedAfterSuccess() {
        val dir = Files.createTempDirectory("sentinel-cleanup-").toFile()
        try {
            val path = withTemporaryApk(dir) { file ->
                assertTrue(file.exists())
                file.writeText("test")
                file.absolutePath
            }
            assertFalse(File(path).exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun temporaryFileDeletedAfterException() {
        val dir = Files.createTempDirectory("sentinel-cleanup-failure-").toFile()
        try {
            val caught = assertThrows(IllegalStateException::class.java) {
                withTemporaryApk(dir) { file ->
                    file.writeText("test")
                    throw IllegalStateException("inspection failed")
                }
            }
            assertEquals("inspection failed", caught.message)
            // The persistent lock file is expected; no temporary APK or unexpected file may remain.
            assertEquals(
                setOf(".sentinel_apk_inspection.lock"),
                dir.listFiles().orEmpty().map { it.name }.toSet()
            )
        } finally {
            dir.deleteRecursively()
        }
    }
}
