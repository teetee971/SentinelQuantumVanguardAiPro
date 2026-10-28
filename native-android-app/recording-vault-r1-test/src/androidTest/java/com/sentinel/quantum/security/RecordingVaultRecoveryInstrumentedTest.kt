package com.sentinel.quantum.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecordingVaultRecoveryInstrumentedTest {

    @Test
    fun abandonedTemporaryFileDoesNotBlockNewRecording() {
        val context =
            InstrumentationRegistry.getInstrumentation().targetContext

        val directory = File(
            context.noBackupFilesDir,
            "recording-vault-r2"
        )

        val vault = RecordingVaultStore(context)
        val id = "recovery_" +
            UUID.randomUUID().toString().replace("-", "")

        assertTrue(directory.isDirectory || directory.mkdirs())

        val residue = File.createTempFile(
            "r2-recovery-test-",
            ".part",
            directory
        )

        try {
            residue.writeBytes(byteArrayOf(1, 2, 3, 4))

            val original = "Recovery verification".toByteArray()
            vault.save(id, original)

            assertArrayEquals(original, vault.read(id))
            assertTrue(residue.exists())
        } finally {
            vault.delete(id)
            residue.delete()
        }
    }
}
