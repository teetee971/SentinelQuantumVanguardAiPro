package com.sentinel.quantum.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.file.Files
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecordingVaultSecurityInstrumentedTest {

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun id() =
        "security_" + UUID.randomUUID().toString().replace("-", "")

    @Test
    fun oversizedPlaintextIsRejected() {
        val vault = RecordingVaultStore(context)
        val recordingId = id()

        assertThrows(IllegalArgumentException::class.java) {
            vault.save(recordingId, ByteArray(16 * 1024 * 1024 + 1))
        }

        assertFalse(vault.exists(recordingId))
    }

    @Test
    fun symbolicLinkAsRecordingIsRejected() {
        val vault = RecordingVaultStore(context)
        val recordingId = id()

        val directory = File(context.noBackupFilesDir, "recording-vault-r2")
        assertTrue(directory.isDirectory || directory.mkdirs())

        val external = File(context.cacheDir, "$recordingId.bin")
        val link = File(directory, "$recordingId.rv")

        external.writeText("protected")

        try {
            Files.createSymbolicLink(
                link.toPath(),
                external.toPath()
            )

            assertThrows(IllegalStateException::class.java) {
                vault.read(recordingId)
            }

            assertEquals("protected", external.readText())
        } finally {
            Files.deleteIfExists(link.toPath())
            external.delete()
        }
    }

    @Test
    fun symbolicLinkAsVaultDirectoryIsRejected() {
        val directoryName = "vault_symlink_" +
            UUID.randomUUID().toString().replace("-", "")

        val directory = File(context.noBackupFilesDir, directoryName)
        val external = File(context.cacheDir, directoryName)

        assertTrue(external.mkdir())

        try {
            Files.createSymbolicLink(
                directory.toPath(),
                external.toPath()
            )

            val vault = RecordingVaultStore(context, directoryName)

            assertThrows(IllegalStateException::class.java) {
                vault.save(id(), "blocked".toByteArray())
            }

            assertTrue(Files.isSymbolicLink(directory.toPath()))
            assertTrue(external.listFiles().isNullOrEmpty())
        } finally {
            Files.deleteIfExists(directory.toPath())
            external.delete()
        }
    }
}
