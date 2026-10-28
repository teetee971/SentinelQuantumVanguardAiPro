package com.sentinel.quantum.security

import android.os.Process
import android.util.Base64
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Properties
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Each phase MUST be a separate am instrument invocation. See qualification/run-device.sh. */
@RunWith(AndroidJUnit4::class)
class RecordingVaultPersistenceInstrumentedTest {
    @Test
    fun persistenceAcrossProcessDeath() {
        val args = InstrumentationRegistry.getArguments()
        val phase = args.getString("vaultPhase")
        assumeTrue("Requires the external force-stop protocol", phase != null)
        val run = requireNotNull(args.getString("vaultRun"))
        require(Regex("[a-f0-9]{32}").matches(run))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = "r3persist_$run"
        val marker = File(context.noBackupFilesDir, "r3-$run.properties")
        val residue = File(context.noBackupFilesDir, "recording-vault-r2/r2-$run.part")
        val stored = File(context.noBackupFilesDir, "recording-vault-r2/$id.rv")
        val payload = ByteArray(4096) { ((it + run[0].code) % 251).toByte() }
        val challenge = "Sentinel R3 key continuity $run".toByteArray()
        val vault = RecordingVaultStore(context)
        fun processIdentity(): String {
            // PID + kernel start ticks: PID alone may be reused after force-stop.
            val stat = File("/proc/self/stat").readText().substringAfterLast(") ").split(' ')
            return "${Process.myPid()}:${stat[19]}"
        }
        fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it) }
        fun keyDate(): String {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            assertTrue(ks.containsAlias("sentinel_recording_vault_r1"))
            return ks.getCreationDate("sentinel_recording_vault_r1")?.time?.toString() ?: "unavailable"
        }
        fun loadMarker() = Properties().apply { marker.inputStream().use { load(it) } }
        fun saveMarker(p: Properties) {
            FileOutputStream(marker).use { p.store(it, "R3 test evidence, no key material"); it.fd.sync() }
        }
        when (phase) {
            "write" -> {
                assertFalse("Use a fresh run identifier", marker.exists())
                assertFalse(vault.exists(id))
                vault.save(id, payload)
                assertArrayEquals(payload, vault.read(id))
                val encryptedChallenge = RecordingVaultCrypto.encrypt(challenge)
                val p = Properties().apply {
                    setProperty("writer", processIdentity())
                    setProperty("hash", digest(stored.readBytes()))
                    setProperty("challenge", Base64.encodeToString(encryptedChallenge, Base64.NO_WRAP))
                    setProperty("keyDate", keyDate())
                }
                saveMarker(p)
                Log.i("SentinelR3", "WRITE run=$run process=${processIdentity()} hash=${p.getProperty("hash")}")
            }
            "stageInterruptedWrite" -> {
                val p = loadMarker()
                assertNotEquals(p.getProperty("writer"), processIdentity())
                assertFalse(residue.exists())
                // Deterministic fault at the partial-temp phase. This deliberately
                // models a write phase; it does not claim to interrupt Store.save itself.
                FileOutputStream(residue).use { stream ->
                    stream.write(byteArrayOf(1, 2, 3, 4))
                    stream.fd.sync()
                    p.setProperty("interruptedWriter", processIdentity())
                    saveMarker(p)
                    Log.i("SentinelR3", "INTERRUPT_READY run=$run process=${processIdentity()}")
                    // External force-stop kills us with the partial file still open.
                    Thread.sleep(120_000)
                    fail("Host did not force-stop the staged writer")
                }
            }
            "verify" -> {
                val p = loadMarker()
                assertNotEquals("A new Store in the same process is insufficient", p.getProperty("writer"), processIdentity())
                assertNotNull(p.getProperty("interruptedWriter"))
                assertNotEquals(p.getProperty("interruptedWriter"), processIdentity())
                assertEquals(p.getProperty("hash"), digest(stored.readBytes()))
                assertEquals(p.getProperty("keyDate"), keyDate())
                assertArrayEquals(payload, vault.read(id))
                // Successful decryption of a PRE-death random-IV challenge is key
                // continuity evidence; alias name/creation date alone are not proof.
                assertArrayEquals(challenge, RecordingVaultCrypto.decrypt(Base64.decode(p.getProperty("challenge"), Base64.NO_WRAP)))
                assertArrayEquals(byteArrayOf(1, 2, 3, 4), residue.readBytes())
                assertFalse(vault.exists("r2-$run"))
                val next = "r3next_$run"
                vault.save(next, payload)
                try { assertArrayEquals(payload, vault.read(next)) } finally { vault.delete(next) }
                Log.i("SentinelR3", "VERIFIED run=$run process=${processIdentity()} writer=${p.getProperty("writer")} interrupted=${p.getProperty("interruptedWriter")}")
            }
            "cleanup" -> {
                assertTrue(vault.delete(id))
                if (residue.exists()) assertTrue(residue.delete())
                if (marker.exists()) assertTrue(marker.delete())
                Log.i("SentinelR3", "CLEANUP run=$run")
            }
            else -> error("Unknown vaultPhase: $phase")
        }
    }
}
