package com.sentinel.quantum.security

import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ApkArchiveValidatorTest {
    private fun fixture(entries: List<String>): File {
        val f = File.createTempFile("sentinel-archive-test-", ".apk")
        ZipOutputStream(f.outputStream()).use { out ->
            for (name in entries) {
                out.putNextEntry(ZipEntry(name))
                out.write(byteArrayOf(1, 2, 3))
                out.closeEntry()
            }
        }
        return f
    }

    @Test fun emptyInputRejected() {
        val f = File.createTempFile("sentinel-empty-", ".apk")
        try { assertThrows(IllegalArgumentException::class.java) { ApkArchiveValidator.validate(f) } }
        finally { f.delete() }
    }

    @Test fun arbitraryDocumentRejected() {
        val f = File.createTempFile("sentinel-random-", ".apk")
        try {
            f.writeText("not an apk")
            assertThrows(IllegalArgumentException::class.java) { ApkArchiveValidator.validate(f) }
        } finally { f.delete() }
    }

    @Test fun missingManifestRejected() {
        val f = fixture(listOf("classes.dex"))
        try { assertThrows(IllegalArgumentException::class.java) { ApkArchiveValidator.validate(f) } }
        finally { f.delete() }
    }

    @Test fun missingDexRejected() {
        val f = fixture(listOf("AndroidManifest.xml"))
        try { assertThrows(IllegalArgumentException::class.java) { ApkArchiveValidator.validate(f) } }
        finally { f.delete() }
    }

    @Test fun validStructureAcceptedButNotSignatureCertified() {
        val f = fixture(listOf("AndroidManifest.xml", "classes.dex"))
        try { ApkArchiveValidator.validate(f) }
        finally { f.delete() }
    }


    @Test fun traversalEntryRejected() {
        val f = fixture(listOf("AndroidManifest.xml", "classes.dex", "../outside.txt"))
        try { assertThrows(IllegalArgumentException::class.java) { ApkArchiveValidator.validate(f) } }
        finally { f.delete() }
    }

    @Test fun excessiveCompressionRejected() {
        val f = File.createTempFile("sentinel-bomb-", ".apk")
        try {
            ZipOutputStream(f.outputStream()).use { out ->
                for (name in listOf("AndroidManifest.xml", "classes.dex")) {
                    out.putNextEntry(ZipEntry(name))
                    out.write(ByteArray(8_000_000))
                    out.closeEntry()
                }
            }
            ZipFile(f).use { zip ->
                val entry = zip.getEntry("classes.dex")
                org.junit.Assert.assertTrue(
                    "Fixture must exceed the 1024:1 compression threshold",
                    entry.size > 1024L * maxOf(1L, entry.compressedSize)
                )
            }
            assertThrows(IllegalArgumentException::class.java) { ApkArchiveValidator.validate(f) }
        } finally { f.delete() }
    }


    @Test fun oversizedManifestIsRejectedDuringBoundedStream() {
        val f = File.createTempFile("sentinel-large-manifest-", ".apk")
        try {
            ZipOutputStream(f.outputStream()).use { out ->
                out.putNextEntry(ZipEntry("AndroidManifest.xml"))
                val random = java.util.Random(42L)
                val block = ByteArray(64 * 1024)
                repeat(140) {
                    random.nextBytes(block)
                    out.write(block)
                }
                out.closeEntry()
                out.putNextEntry(ZipEntry("classes.dex"))
                out.write(byteArrayOf(1, 2, 3))
                out.closeEntry()
            }
            assertThrows(IllegalArgumentException::class.java) { ApkArchiveValidator.validate(f) }
        } finally { f.delete() }
    }

    @Test fun truncatedZipRejected() {
        val f = fixture(listOf("AndroidManifest.xml", "classes.dex"))
        try {
            val bytes = f.readBytes()
            f.writeBytes(bytes.copyOf(bytes.size / 2))
            assertThrows(IllegalArgumentException::class.java) { ApkArchiveValidator.validate(f) }
        } finally { f.delete() }
    }
}
