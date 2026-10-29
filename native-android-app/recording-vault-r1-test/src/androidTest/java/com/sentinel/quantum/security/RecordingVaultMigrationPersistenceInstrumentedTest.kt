package com.sentinel.quantum.security

import android.os.Process
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Properties
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real migration is suspended at an I/O boundary; external adb force-stop kills it. */
@RunWith(AndroidJUnit4::class)
class RecordingVaultMigrationPersistenceInstrumentedTest {
    @Test fun migrationAcrossProcessDeath() {
        val args = InstrumentationRegistry.getArguments()
        val phase = args.getString("vaultPhase")
        assumeTrue("External protocol required", phase != null)
        val run = requireNotNull(args.getString("vaultRun"))
        val point = requireNotNull(args.getString("vaultPoint"))
        require(Regex("[a-f0-9]{32}").matches(run))
        require(point in listOf("prepared", "required", "published"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "mig_${run}_$point"
        val dir = File(context.noBackupFilesDir, name)
        val source = File(dir, "item.rv")
        val witness = File(dir, "qualification.properties")
        val payload = "migration-$run-$point".toByteArray()
        fun identity(): String {
            val stat = File("/proc/self/stat").readText().substringAfterLast(") ").split(' ')
            return "${Process.myPid()}:${stat[19]}"
        }
        fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        fun save(p: Properties) {
            FileOutputStream(witness).use { p.store(it, "test witness"); it.fd.sync() }
        }
        fun load() = Properties().apply { witness.inputStream().use { load(it) } }
        val store = RecordingVaultStore(context, name)
        when (phase) {
            "setup" -> {
                assertFalse("Fresh run required; never delete previous data", dir.exists())
                store.exists("init")
                FileOutputStream(source).use { it.write(RecordingVaultCrypto.encrypt(payload)); it.fd.sync() }
                VaultIo().syncDirectory(dir.toPath())
                save(Properties().apply {
                    setProperty("writer", identity())
                    setProperty("sourceHash", hash(source.readBytes()))
                })
                assertArrayEquals(payload, store.read("item"))
            }
            "interrupt" -> {
                val p = load()
                assertNotEquals(p.getProperty("writer"), identity())
                fun suspendMigration() {
                    p.setProperty("interrupted", identity())
                    save(p)
                    Log.i("SentinelR3", "MIGRATION_READY run=$run point=$point process=${identity()}")
                    Thread.sleep(120000)
                    fail("External force-stop was not performed")
                }
                val io = object : VaultIo() {
                    override fun syncDirectory(path: Path) {
                        super.syncDirectory(path)
                        if (point == "prepared" && path.toFile().canonicalFile == dir.canonicalFile &&
                            File(dir, "item.v2.pending").exists() && !File(dir, "item.v2.required").exists()) suspendMigration()
                    }
                    override fun publish(source: Path, destination: Path) {
                        super.publish(source, destination)
                        if (point == "required" && destination.toString().endsWith(".v2.required") ||
                            point == "published" && destination.toString().endsWith(".v2.rv")) suspendMigration()
                    }
                }
                RecordingVaultStore(context, name, io).migrate("item")
                fail("Migration did not reach interruption point")
            }
            "verify" -> {
                val p = load()
                assertNotNull(p.getProperty("interrupted"))
                assertNotEquals(p.getProperty("writer"), identity())
                assertNotEquals(p.getProperty("interrupted"), identity())
                store.migrate("item")
                assertArrayEquals(payload, store.read("item"))
                assertEquals(p.getProperty("sourceHash"), hash(source.readBytes()))
                val final = File(dir, "item.v2.rv").readBytes()
                assertFalse(store.migrate("item"))
                assertArrayEquals(final, File(dir, "item.v2.rv").readBytes())
            }
            "cleanup" -> {
                assertTrue(store.delete("item"))
                assertTrue(witness.delete())
                assertTrue(dir.delete()) // Only the unique test directory, no global key/lock removal.
            }
            else -> error("Unknown migration phase")
        }
    }
}
