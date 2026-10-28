package com.sentinel.quantum.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.file.Files
import java.util.UUID
import javax.crypto.AEADBadTagException
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecordingVaultR3InstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun withVault(body: (RecordingVaultStore, File) -> Unit) {
        val name = "r3test_" + UUID.randomUUID().toString().replace("-", "")
        val directory = File(context.noBackupFilesDir, name)
        try { body(RecordingVaultStore(context, name), directory) }
        finally { directory.deleteRecursively() }
    }

    @Test fun exactSizeLimits() = withVault { vault, dir ->
        assertThrows(IllegalArgumentException::class.java) { vault.save("empty", byteArrayOf()) }
        assertThrows(IllegalArgumentException::class.java) { vault.save("tooBig", ByteArray(16 * 1024 * 1024 + 1)) }
        vault.save("one", byteArrayOf(42))
        assertArrayEquals(byteArrayOf(42), vault.read("one"))
        val maximum = ByteArray(16 * 1024 * 1024) { (it % 251).toByte() }
        vault.save("max", maximum)
        assertEquals(16 * 1024 * 1024 + 29L, File(dir, "max.rv").length())
        assertArrayEquals(maximum, vault.read("max"))
        File(dir, "large.rv").outputStream().use { out ->
            repeat(16 * 1024) { out.write(ByteArray(1024)) }
            out.write(ByteArray(30))
        }
        assertThrows(IllegalArgumentException::class.java) { vault.read("large") }
    }

    @Test fun corruptedAndTruncatedEnvelopesFailClosed() = withVault { vault, dir ->
        vault.save("item", "R3 integrity".toByteArray())
        val file = File(dir, "item.rv")
        val valid = file.readBytes()
        for (position in listOf(1, 13, valid.lastIndex)) {
            val bad = valid.copyOf()
            bad[position] = (bad[position].toInt() xor 1).toByte()
            file.writeBytes(bad)
            assertThrows(AEADBadTagException::class.java) { vault.read("item") }
        }
        for (length in listOf(0, 1, 12, 28, 29)) {
            file.writeBytes(valid.copyOf(length))
            assertThrows(IllegalArgumentException::class.java) { vault.read("item") }
        }
        file.writeBytes(valid.copyOf(valid.size - 1))
        assertThrows(AEADBadTagException::class.java) { vault.read("item") }
        val version = valid.copyOf().apply { this[0] = 99 }
        file.writeBytes(version)
        assertThrows(IllegalArgumentException::class.java) { vault.read("item") }
    }

    @Test fun danglingDestinationCannotBeReplaced() = withVault { vault, dir ->
        vault.exists("initialize")
        val absent = File(context.cacheDir, UUID.randomUUID().toString())
        val link = File(dir, "item.rv").toPath()
        Files.createSymbolicLink(link, absent.toPath())
        try {
            assertThrows(IllegalStateException::class.java) { vault.save("item", byteArrayOf(1)) }
            assertThrows(IllegalStateException::class.java) { vault.read("item") }
            assertThrows(IllegalStateException::class.java) { vault.delete("item") }
            assertTrue(Files.isSymbolicLink(link))
            assertFalse(absent.exists())
        } finally { Files.deleteIfExists(link) }
    }

    @Test fun validCiphertextReplacementIsAnExplicitLegacyLimitation() = withVault { vault, dir ->
        // Characterization, not a security PASS: v1 has no AAD binding to recording ID.
        vault.save("a", "first".toByteArray())
        vault.save("b", "second".toByteArray())
        File(dir, "a.rv").writeBytes(File(dir, "b.rv").readBytes())
        assertArrayEquals("second".toByteArray(), vault.read("a"))
    }
}
