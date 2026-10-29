package com.sentinel.quantum.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** AndroidKeyStore + real filesystem + ARM64 JNI. Host results do not qualify these tests. */
@RunWith(AndroidJUnit4::class)
class RecordingVaultMigrationInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val data = "migration qualification payload".toByteArray()
    private fun fixture(body: (RecordingVaultStore, File, String) -> Unit) {
        val name = "migration_" + UUID.randomUUID().toString().replace("-", "")
        val dir = File(context.noBackupFilesDir, name)
        try {
            val store = RecordingVaultStore(context, name)
            store.exists("init")
            File(dir, "item.rv").writeBytes(RecordingVaultCrypto.encrypt(data))
            body(store, dir, name)
        } finally { dir.deleteRecursively() } // Only this test's fresh random directory.
    }
    private fun rejected(block: () -> Unit) {
        assertTrue("Expected explicit rejection", runCatching(block).isFailure)
    }

    @Test fun legacyReadBeforeMigration() = fixture { store, _, _ -> assertArrayEquals(data, store.read("item")) }
    @Test fun migrationPreservesSourceAndUsesFreshIV() = fixture { store, dir, _ ->
        val original = File(dir, "item.rv").readBytes()
        assertTrue(store.migrate("item"))
        assertArrayEquals(original, File(dir, "item.rv").readBytes())
        val migrated = File(dir, "item.v2.rv").readBytes()
        assertEquals(2, migrated[0].toInt())
        assertFalse(original.copyOfRange(1, 13).contentEquals(migrated.copyOfRange(1, 13)))
    }
    @Test fun migratedPayloadIsAuthenticated() = fixture { store, _, _ ->
        store.migrate("item")
        assertArrayEquals(data, store.read("item"))
    }
    @Test fun repeatedMigrationDoesNotRewrite() = fixture { store, dir, _ ->
        store.migrate("item")
        val final = File(dir, "item.v2.rv").readBytes()
        val marker = File(dir, "item.v2.required").readBytes()
        assertFalse(store.migrate("item"))
        assertArrayEquals(final, File(dir, "item.v2.rv").readBytes())
        assertArrayEquals(marker, File(dir, "item.v2.required").readBytes())
    }
    @Test fun partialPreparationPreservesSource() = fixture { store, dir, name ->
        val original = File(dir, "item.rv").readBytes()
        val io = object : VaultIo() {
            override fun writeForced(path: Path, bytes: ByteArray) {
                Files.write(path, bytes.copyOf(9))
                throw IOException("Injected ENOSPC")
            }
        }
        rejected { RecordingVaultStore(context, name, io).migrate("item") }
        assertArrayEquals(original, File(dir, "item.rv").readBytes())
        assertArrayEquals(data, store.read("item"))
        assertTrue(store.migrate("item"))
    }
    @Test fun interruptionAfterIntentRecovers() = fixture { store, _, name ->
        val io = object : VaultIo() {
            override fun publish(source: Path, destination: Path) {
                super.publish(source, destination)
                if (destination.toString().endsWith(".v2.required")) throw IOException("Injected after intent")
            }
        }
        rejected { RecordingVaultStore(context, name, io).migrate("item") }
        assertArrayEquals(data, store.read("item"))
    }
    @Test fun publicationSyncFailureCannotReportSuccess() = fixture { store, dir, name ->
        val io = object : VaultIo() {
            override fun syncDirectory(path: Path) {
                if (File(dir, "item.v2.rv").exists()) throw IOException("Injected directory fsync failure")
                super.syncDirectory(path)
            }
        }
        rejected { RecordingVaultStore(context, name, io).migrate("item") }
        assertArrayEquals(data, store.read("item"))
        assertFalse(store.migrate("item"))
    }
    @Test fun corruptOrTruncatedSourceIsNeverConverted() = fixture { store, dir, _ ->
        val original = File(dir, "item.rv").readBytes()
        for (bad in listOf(original.copyOf(12), original.copyOf(original.size - 1))) {
            File(dir, "item.rv").writeBytes(bad)
            rejected { store.migrate("item") }
            assertArrayEquals(bad, File(dir, "item.rv").readBytes())
            assertFalse(File(dir, "item.v2.rv").exists())
        }
    }
    @Test fun substitutedV2IdentifierIsRejected() = fixture { store, dir, _ ->
        store.migrate("item")
        store.save("other", data)
        File(dir, "item.v2.rv").writeBytes(File(dir, "other.rv").readBytes())
        rejected { store.read("item") }
    }
    @Test fun substitutedV2DirectoryIsRejected() = fixture { store, dir, _ ->
        store.migrate("item")
        File(dir, "item.v2.rv").writeBytes(RecordingVaultCrypto.encryptV2(data, "foreign", "item"))
        rejected { store.read("item") }
    }
    @Test fun authenticV1DowngradeIsRejected() = fixture { store, dir, _ ->
        store.migrate("item")
        File(dir, "item.v2.rv").writeBytes(File(dir, "item.rv").readBytes())
        rejected { store.read("item") }
        rejected { store.migrate("item") }
    }
    @Test fun concurrentMigrationAndSaveCannotOverwrite() = fixture { store, dir, name ->
        val pool = java.util.concurrent.Executors.newFixedThreadPool(4)
        try {
            val futures = (1..4).map {
                pool.submit<Boolean> { RecordingVaultStore(context, name).migrate("item") }
            }
            futures.forEach { it.get(15, java.util.concurrent.TimeUnit.SECONDS) }
        } finally { pool.shutdownNow() }
        assertArrayEquals(data, store.read("item"))
        val original = File(dir, "item.v2.rv").readBytes()
        rejected { store.save("item", "replacement".toByteArray()) }
        assertArrayEquals(original, File(dir, "item.v2.rv").readBytes())
    }
    @Test fun missingCandidateAfterIntentPreservesRecoverableV1() = fixture { store, dir, name ->
        val original = File(dir, "item.rv").readBytes()
        val io = object : VaultIo() {
            override fun publish(source: Path, destination: Path) {
                if (destination.toString().endsWith(".v2.rv")) throw IOException("Injected before publish")
                super.publish(source, destination)
            }
        }
        rejected { RecordingVaultStore(context, name, io).migrate("item") }
        assertTrue(File(dir, "item.v2.pending").delete())
        rejected { store.read("item") }
        assertArrayEquals(original, File(dir, "item.rv").readBytes())
    }
    @Test fun interruptedNewSaveCanRecoverPending() = fixture { _, dir, name ->
        val io = object : VaultIo() {
            override fun syncDirectory(path: Path) {
                super.syncDirectory(path)
                if (File(dir, "new.v2.pending").exists()) throw IOException("Injected before intent")
            }
        }
        rejected { RecordingVaultStore(context, name, io).save("new", data) }
        assertArrayEquals(data, RecordingVaultStore(context, name).read("new"))
    }
}
