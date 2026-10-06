package com.sentinel.quantum.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CallFingerprintConcurrencyInstrumentationTest {
    @Test fun concurrentFirstUseAcrossInstancesKeepsOneNonExportableKey() {
        // A unique test version exercises real first-use creation without deleting or replacing
        // the application's v1/v2 aliases or modifying any user protection rule.
        val version = "test-" + UUID.randomUUID().toString()
        val alias = "sentinel_call_rule_hmac_" + version
        val method = CallNumberFingerprinter::class.java.getDeclaredMethod(
            "fingerprint", String::class.java, String::class.java, Boolean::class.javaPrimitiveType!!
        ).apply { isAccessible = true }
        val workers = Executors.newFixedThreadPool(8)
        val ready = CountDownLatch(8)
        val start = CountDownLatch(1)
        try {
            val results = (1..8).map {
                workers.submit<String?> {
                    val fingerprinter = CallNumberFingerprinter()
                    ready.countDown()
                    check(start.await(10, TimeUnit.SECONDS))
                    method.invoke(fingerprinter, "+15550100", version, true) as String?
                }
            }
            assertTrue("All key consumers reached the first-use boundary", ready.await(10, TimeUnit.SECONDS))
            start.countDown()
            val fingerprints = results.map { it.get(30, TimeUnit.SECONDS) }
            fingerprints.forEach { assertNotNull("Keystore fingerprint creation succeeded", it) }
            assertEquals("Concurrent instances must use the same key", 1, fingerprints.toSet().size)
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val key = keyStore.getKey(alias, null)
            assertNotNull("The alias exists in Android Keystore", key)
            assertNull("The HMAC key must remain non-exportable", key.encoded)
        } finally {
            start.countDown()
            workers.shutdownNow()
            workers.awaitTermination(30, TimeUnit.SECONDS)
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
        }
    }
}
