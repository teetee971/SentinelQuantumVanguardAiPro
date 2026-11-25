package com.sentinel.quantum.ai

import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** C11 persistence fixtures. A physical receipt requires a successful, explicit verify phase. */
@RunWith(AndroidJUnit4::class)
class SentinelAiPersistenceQualificationTest {

    companion object {
        @kotlin.jvm.JvmStatic
        @get:org.junit.ClassRule
        val refuseInstalledPackage = com.sentinel.quantum.security.RefuseInstalledPackageRule()
    }

    private fun store() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private fun createKey(alias: String): SecretKey = KeyGenerator.getInstance(
        KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore").run {
        init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
            .setDigests(KeyProperties.DIGEST_SHA256).build())
        generateKey()
    }
    @Test fun concurrentAndroidKeystoreJournalWriters() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        com.sentinel.quantum.security.InstalledDataGuard.assertSafeToRun(
            instrumentation.targetContext,
            instrumentation.context
        )
        val context = instrumentation.targetContext
        val alias = "sentinel_ai_concurrency_test_" + UUID.randomUUID()
        val directory = File(context.cacheDir, alias)
        val keys = store();val pool = Executors.newFixedThreadPool(4)
        try {
            val key = createKey(alias);assertNull(key.encoded)
            val journal = SentinelAiAuditJournal.create(directory, key)
            val writers = List(4) { SentinelAiAuditJournal.open(directory, keys.getKey(alias,null) as SecretKey) }
            val results = (0 until 20).map { index -> pool.submit<Long> {
                writers[index % 4].append(SentinelAiAuditJournal.Kind.REQUEST, SystemClock.elapsedRealtime()).sequence
            } }.map { it.get(15, TimeUnit.SECONDS) }
            assertEquals((1L..20L).toSet(), results.toSet())
            assertEquals(20L, journal.verify().sequence)
        } finally {
            pool.shutdownNow()
            if (keys.containsAlias(alias)) keys.deleteEntry(alias)
            directory.deleteRecursively() // UUID-named fixture only.
        }
    }
    @Test fun journalPersistenceFixtureProtocol() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        com.sentinel.quantum.security.InstalledDataGuard.assertSafeToRun(
            instrumentation.targetContext,
            instrumentation.context
        )
        val context = instrumentation.targetContext
        val arguments = InstrumentationRegistry.getArguments()
        val phase = arguments.getString("sentinel_ai_restart_phase") ?: "roundtrip"
        check(phase in setOf("roundtrip", "prepare", "verify")) { "Unknown C11 fixture phase" }
        val roundtrip = phase == "roundtrip"
        if (!roundtrip) check(context.packageName == SENTINEL_AI_C11_PACKAGE) {
            "Persistent fixtures require the dedicated C11 validation APK"
        }
        val kindArgument = arguments.getString("sentinel_ai_restart_kind") ?: "process"
        val kind = SentinelAiRestartKind.entries.singleOrNull { it.argument == kindArgument }
            ?: error("Unknown C11 restart kind")
        val run = if (roundtrip) UUID.randomUUID().toString() else {
            val value = checkNotNull(arguments.getString("sentinel_ai_run_id")) { "C11 run ID required" }
            check(value.length == 36 && UUID.fromString(value).toString() == value) { "Invalid C11 run ID" }
            value
        }
        val scope = if (roundtrip) "roundtrip" else kind.argument
        val alias = "sentinel_ai_c11_restart_${scope}_$run"
        val directory = File(context.noBackupFilesDir, alias)
        val metadataFile = File(directory, "restart-metadata")
        val keys = store()
        val identity = SentinelAiRestartIdentity.fromProcStat(
            File("/proc/self/stat").readText(Charsets.US_ASCII),
            Process.myPid(),
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
        )

        fun receipt(prepared: Boolean, verified: Boolean, sequence: Long) {
            instrumentation.sendStatus(0, Bundle().apply {
                putString("cycle11_phase", phase)
                putString("cycle11_run_id", run)
                putString("cycle11_restart_kind", kind.argument)
                putString("cycle11_pid", identity.pid.toString())
                putString("cycle11_start_ticks", identity.startTicks.toString())
                putString("cycle11_boot_count", identity.bootCount.toString())
                putString("cycle11_sequence", sequence.toString())
                putString("cycle11_prepare_committed", prepared.toString())
                putString("cycle11_restart_verified", (verified && kind == SentinelAiRestartKind.PROCESS).toString())
                putString("cycle11_reboot_verified", (verified && kind == SentinelAiRestartKind.REBOOT).toString())
            })
        }

        fun readIdentity(key: SecretKey): SentinelAiRestartIdentity {
            check(metadataFile.length() in 1..SentinelAiRestartIdentity.MAX_AUTHENTICATED_LENGTH) {
                "Missing or oversized C11 process metadata"
            }
            return SentinelAiRestartIdentity.verifyAuthenticated(metadataFile.readText(Charsets.US_ASCII), key, alias)
        }

        if (phase == "verify") {
            check(keys.containsAlias(alias) && directory.isDirectory) { "Prepared C11 fixture missing" }
            val key = keys.getKey(alias, null) as SecretKey
            assertNull(key.encoded)
            val previous = readIdentity(key)
            check(identity.provesRestartFrom(previous, kind)) { "Requested C11 restart was not demonstrated" }
            val journal = SentinelAiAuditJournal.open(directory, key)
            assertEquals(1L, journal.verify().sequence)
            val appended = journal.append(SentinelAiAuditJournal.Kind.REVOKED, SystemClock.elapsedRealtime())
            assertEquals(2L, appended.sequence)
            assertEquals(appended, journal.verify())
            receipt(prepared = false, verified = true, sequence = appended.sequence)
            return
        }

        check(!directory.exists() && !keys.containsAlias(alias)) { "C11 fixture exists; use a new run ID" }
        val key = createKey(alias)
        try {
            assertNull(key.encoded)
            val journal = SentinelAiAuditJournal.create(directory, key)
            val appended = journal.append(SentinelAiAuditJournal.Kind.SESSION_OPEN, SystemClock.elapsedRealtime())
            FileOutputStream(metadataFile).use { output ->
                output.write(identity.authenticate(key, alias).toByteArray(Charsets.US_ASCII))
                output.fd.sync()
            }
            assertEquals(identity, readIdentity(key))
            assertEquals(1L, appended.sequence)
            assertEquals(appended, SentinelAiAuditJournal.open(directory, key).verify())
            receipt(prepared = true, verified = false, sequence = appended.sequence)
            if (!roundtrip) {
                // Keep the instrumented target alive until the operator issues the explicit stop or reboot.
                CountDownLatch(1).await(180, TimeUnit.SECONDS)
                error("C11 prepare timed out waiting for the explicit stop or reboot")
            }
        } finally {
            if (roundtrip) {
                keys.deleteEntry(alias)
                directory.deleteRecursively()
            }
        }
    }
}
