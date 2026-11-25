package com.sentinel.quantum.ai

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.IOException
import java.security.KeyStore
import java.util.UUID
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real AndroidKeyStore checks. Compilation is not execution evidence. Never uses production alias. */
@RunWith(AndroidJUnit4::class)
class SentinelAiAuditJournalInstrumentationTest {

    companion object {
        @kotlin.jvm.JvmStatic
        @get:org.junit.ClassRule
        val refuseInstalledPackage = com.sentinel.quantum.security.RefuseInstalledPackageRule()
    }

    @Test fun androidKeystoreReopenAndTamperingRejection() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        com.sentinel.quantum.security.InstalledDataGuard.assertSafeToRun(
            instrumentation.targetContext,
            instrumentation.context
        )
        val alias = "sentinel_ai_audit_test_" + UUID.randomUUID()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, alias)
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        try {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore")
            generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setDigests(KeyProperties.DIGEST_SHA256).build())
            val key = generator.generateKey()
            assertNull(key.encoded) // AndroidKeyStore key material must not be exported.
            val journal = SentinelAiAuditJournal.create(directory, key)
            journal.append(SentinelAiAuditJournal.Kind.SESSION_OPEN, android.os.SystemClock.elapsedRealtime())
            val loaded = store.getKey(alias, null) as SecretKey
            assertEquals(1L, SentinelAiAuditJournal.open(directory, loaded).verify().sequence)
            val events = File(directory, "events")
            events.writeText(events.readText().replace("SESSION_OPEN", "CHECKPOINT"))
            assertThrows(IOException::class.java) { journal.verify() }
        } finally {
            if (store.containsAlias(alias)) store.deleteEntry(alias)
            directory.deleteRecursively() // Only this test's UUID-named cache directory.
        }
    }
}
