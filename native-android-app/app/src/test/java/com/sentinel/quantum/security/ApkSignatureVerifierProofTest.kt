package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Real apksig verifier proof, never installs or modifies a device application.
 * Provide an explicitly chosen, signed fixture via -PsentinelSignatureFixture=/absolute/path.apk
 * (forwarded to the test JVM as sentinel.signature.fixture).
 */
class ApkSignatureVerifierProofTest {
    @Test fun validAndCorruptedApkAreDistinguished() {
        val fixture = System.getProperty("sentinel.signature.fixture")
        assumeTrue("SIGNED_APK_FIXTURE_REQUIRED", !fixture.isNullOrBlank())
        val source = File(fixture!!)
        assumeTrue("SIGNED_APK_FIXTURE_NOT_FOUND", source.isFile)
        val temporary = Files.createTempDirectory("sentinel-apksig-proof").toFile()
        try {
            val original = File(temporary, "original.apk")
            val corrupted = File(temporary, "corrupted.apk")
            source.copyTo(original, overwrite = false)
            assertEquals("Signature of the known-good fixture", true, ApkSignatureVerifier.verify(original))
            val bytes = original.readBytes()
            require(bytes.size > 4096) { "APK too small" }
            corrupted.writeBytes(bytes + byteArrayOf(0x58))
            assertFalse("Mutated copy must never verify", ApkSignatureVerifier.verify(corrupted) == true)
        } finally {
            temporary.deleteRecursively()
        }
    }
}
