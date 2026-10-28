package com.sentinel.quantum.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecordingVaultStoreInstrumentedTest {

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun newId(): String =
        "test_" + UUID.randomUUID().toString().replace("-", "")

    @Test
    fun encryptedRecordingPersistsAcrossStoreInstances() {
        val id = newId()
        val original = "Confidential R2 audio".toByteArray()

        val first = RecordingVaultStore(context)
        first.save(id, original)

        try {
            val second = RecordingVaultStore(context)

            assertTrue(second.exists(id))
            assertArrayEquals(original, second.read(id))

            val stored = File(
                context.noBackupFilesDir,
                "recording-vault-r2/$id.rv"
            ).readBytes()

            assertFalse(stored.contentEquals(original))
            assertFalse(
                stored.toString(Charsets.UTF_8)
                    .contains("Confidential R2 audio")
            )
        } finally {
            first.delete(id)
        }
    }

    @Test
    fun duplicateRecordingIsRejected() {
        val id = newId()
        val vault = RecordingVaultStore(context)

        vault.save(id, "First".toByteArray())

        try {
            assertThrows(IllegalStateException::class.java) {
                vault.save(id, "Second".toByteArray())
            }

            assertArrayEquals(
                "First".toByteArray(),
                vault.read(id)
            )
        } finally {
            vault.delete(id)
        }
    }

    @Test
    fun invalidIdentifiersAreRejected() {
        val vault = RecordingVaultStore(context)

        for (id in listOf("", "../escape", "a/b", "a.b")) {
            assertThrows(IllegalArgumentException::class.java) {
                vault.save(id, "Test".toByteArray())
            }
        }
    }

    @Test
    fun corruptedCiphertextIsRejected() {
        val id = newId()
        val vault = RecordingVaultStore(context)

        vault.save(id, "Integrity test".toByteArray())

        try {
            val file = File(
                context.noBackupFilesDir,
                "recording-vault-r2/$id.rv"
            )

            val bytes = file.readBytes()
            bytes[bytes.lastIndex] =
                (bytes.last().toInt() xor 1).toByte()

            file.writeBytes(bytes)

            assertThrows(Exception::class.java) {
                vault.read(id)
            }
        } finally {
            vault.delete(id)
        }
    }

    @Test
    fun recordingCanBeDeleted() {
        val id = newId()
        val vault = RecordingVaultStore(context)

        vault.save(id, "Temporary recording".toByteArray())

        assertTrue(vault.exists(id))
        assertTrue(vault.delete(id))
        assertFalse(vault.exists(id))
    }
}
