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
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Physical-device protocol. Default roundtrip is NOT proof of process/device restart.
 * Only explicit verify mode can report restart_verified=true. Never touches the production alias.
 */
@RunWith(AndroidJUnit4::class)
class SentinelAiPersistenceQualificationTest {
    private fun store() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private fun createKey(alias: String): SecretKey = KeyGenerator.getInstance(
        KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore").run {
        init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
            .setDigests(KeyProperties.DIGEST_SHA256).build())
        generateKey()
    }
    private fun sign(key: SecretKey, value: String) = Mac.getInstance("HmacSHA256").run {
        init(key)
        doFinal(("cycle9-restart-fixture|"+value).toByteArray(Charsets.US_ASCII))
            .joinToString("") { "%02x".format(it) }
    }
    @Test fun concurrentAndroidKeystoreJournalWriters() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
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
        val context = instrumentation.targetContext
        val arguments = InstrumentationRegistry.getArguments()
        val phase = arguments.getString("sentinel_ai_restart_phase") ?: "roundtrip"
        check(phase in setOf("roundtrip", "prepare", "verify", "cleanup")) { "Unknown fixture phase" }
        if (phase != "roundtrip") check(context.packageName == "com.sentinel.quantum.aivalidation") {
            "Persistent fixture phases require the isolated validation APK"
        }
        val alias = if (phase == "roundtrip") "sentinel_ai_restart_test_"+UUID.randomUUID()
                    else "sentinel_ai_cycle9_restart_fixture_only"
        val directory = File(context.noBackupFilesDir, alias)
        val keys = store()
        var cleanup = phase == "roundtrip" || phase == "cleanup"
        var restarted = false
        var rebooted = false
        try {
            if (phase == "cleanup") return
            if (phase == "prepare" || phase == "roundtrip") {
                check(!directory.exists() && !keys.containsAlias(alias)) { "Fixture exists: verify it or explicitly clean up; never overwrite" }
                val key = createKey(alias);assertNull(key.encoded)
                val journal = SentinelAiAuditJournal.create(directory,key)
                journal.append(SentinelAiAuditJournal.Kind.SESSION_OPEN,SystemClock.elapsedRealtime())
                val boot = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT,-1)
                val metadata = "${Process.myPid()}|$boot"
                FileOutputStream(File(directory,"restart-metadata")).use { out ->
                    out.write((metadata+"|"+sign(key,metadata)).toByteArray(Charsets.US_ASCII));out.fd.sync()
                }
                assertEquals(1L, SentinelAiAuditJournal.open(directory,keys.getKey(alias,null) as SecretKey).verify().sequence)
            } else {
                check(keys.containsAlias(alias) && directory.isDirectory) { "Prepared fixture missing" }
                val key = keys.getKey(alias,null) as SecretKey;assertNull(key.encoded)
                val parts = File(directory,"restart-metadata").readText().split('|')
                check(parts.size==3)
                val metadata=parts.take(2).joinToString("|")
                check(MessageDigest.isEqual(sign(key,metadata).toByteArray(),parts[2].toByteArray())) { "Fixture metadata altered" }
                val previousPid=parts[0].toInt();val previousBoot=parts[1].toInt()
                val boot=Settings.Global.getInt(context.contentResolver,Settings.Global.BOOT_COUNT,-1)
                rebooted=previousBoot>=0 && boot>previousBoot
                restarted=rebooted || previousPid!=Process.myPid()
                check(restarted) { "A process restart was not demonstrated" }
                if (arguments.getString("sentinel_ai_require_reboot")=="true") check(rebooted) { "A device reboot was not demonstrated" }
                val journal=SentinelAiAuditJournal.open(directory,key)
                assertEquals(1L,journal.verify().sequence)
                assertEquals(2L,journal.append(SentinelAiAuditJournal.Kind.REVOKED,SystemClock.elapsedRealtime()).sequence)
                cleanup=true // Successful fixture verification only; preserve evidence on failure.
            }
        } finally {
            if (cleanup) {
                if (keys.containsAlias(alias)) keys.deleteEntry(alias)
                directory.deleteRecursively() // Only the named fixture, never sentinel-ai-audit-v1.
            }
            instrumentation.sendStatus(0,Bundle().apply {
                putString("cycle9_phase",phase)
                putString("cycle9_restart_verified",restarted.toString())
                putString("cycle9_reboot_verified",rebooted.toString())
            })
        }
    }
}
