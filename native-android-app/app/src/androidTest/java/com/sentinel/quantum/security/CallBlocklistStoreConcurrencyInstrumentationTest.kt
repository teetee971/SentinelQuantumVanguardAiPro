package com.sentinel.quantum.security

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real SharedPreferences-backed store on an Android runtime. */
@RunWith(AndroidJUnit4::class)
class CallBlocklistStoreConcurrencyInstrumentationTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

    @Before
    fun resetUserRules() {
        val store = CallBlocklistStore(context)
        assertTrue(store.clearBlockedNumbers())
        assertTrue(store.replaceBlockedPrefixes(emptySet()))
        assertTrue(store.setArcepVerifiedBlockingEnabled(false))
    }

    @Test
    fun two_store_instances_concurrently_add_prefixes_without_lost_rules() {
        val first = CallBlocklistStore(context)
        val second = CallBlocklistStore(context)
        runConcurrent {
            first.addBlockedPrefix("+33981")
            second.addBlockedPrefix("+33982")
        }
        assertEquals(setOf("+33981", "+33982"), first.snapshot().blockedPrefixes)
    }

    @Test
    fun concurrent_add_and_remove_preserve_the_complete_committed_state() {
        val first = CallBlocklistStore(context)
        val second = CallBlocklistStore(context)
        assertTrue(first.addBlockedPrefix("+33983"))
        runConcurrent {
            first.removeBlockedPrefix("+33983")
            second.addBlockedPrefix("+33984")
        }
        assertFalse(first.snapshot().blockedPrefixes.contains("+33983"))
        assertTrue(first.snapshot().blockedPrefixes.contains("+33984"))
    }

    @Test
    fun purge_and_exact_number_addition_do_not_publish_a_torn_snapshot() {
        val first = CallBlocklistStore(context)
        val second = CallBlocklistStore(context)
        assertTrue(first.addBlockedNumber(
            "0612345678", "expired fixture", CallBlockMetadata.Duration.HOURS_24, now = 1_000L
        ))
        runConcurrent {
            first.purgeExpiredBlockedNumbers(now = 24L * 60L * 60L * 1000L + 1L)
            second.addBlockedNumber(
                "0612345679", "live fixture", CallBlockMetadata.Duration.PERMANENT, now = 2_000L
            )
        }
        val snapshot = first.snapshot(now = 2_000L)
        assertEquals(1, snapshot.blockedNumberHashes.size)
    }

    @Test
    fun replacement_and_snapshot_publication_never_leave_an_old_manual_set() {
        val first = CallBlocklistStore(context)
        val second = CallBlocklistStore(context)
        runConcurrent {
            first.replaceBlockedPrefixes(setOf("+33985", "+33986"))
            second.replaceBlockedPrefixes(setOf("+33987", "+33988"))
        }
        val persisted = first.snapshot().blockedPrefixes
        assertTrue(persisted == setOf("+33985", "+33986") || persisted == setOf("+33987", "+33988"))
        assertEquals(persisted, first.prepareScreeningSnapshot().blockedPrefixes)
        assertEquals(persisted, first.cachedScreeningSnapshot().blockedPrefixes)
    }

    @Test
    fun arcep_activation_and_manual_prefix_addition_are_serialized() {
        val first = CallBlocklistStore(context)
        val second = CallBlocklistStore(context)
        runConcurrent {
            first.setArcepVerifiedBlockingEnabled(true)
            second.addBlockedPrefix("+33989")
        }
        val snapshot = first.snapshot()
        if (ArcepVerifiedPrefixCatalog.e164Prefixes.size < CallRuleEngine.MAX_PREFIX_RULES) {
            assertTrue(snapshot.blockedPrefixes.contains("+33989"))
        }
    }

    @Test
    fun signed_installation_is_serialized_and_replay_is_rejected() {
        val first = CallBlocklistStore(context)
        val second = CallBlocklistStore(context)
        val keyPair = keyPair()
        val verifier = SignedCallRulePackageVerifier(mapOf("trusted" to keyPair.public), "sentinel-rules")
        val current = first.signedRuleMetadata().acceptedSequence ?: 0L
        val envelope = envelope(sequence = current + 1L, signer = keyPair)
        val results = runConcurrent {
            first.installSignedSilenceRules(envelope, verifier, NOW)
            second.installSignedSilenceRules(envelope, verifier, NOW)
        }
        assertEquals(1, results.count { it.accepted })
        assertEquals("SIGNED_RULE_ROLLBACK_REJECTED", first.installSignedSilenceRules(envelope, verifier, NOW).reason)
    }

    private fun <T> runConcurrent(block: () -> T): List<T> {
        val ready = CountDownLatch(2)
        val release = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        return try {
            val futures = (0 until 2).map {
                executor.submit<T> {
                    ready.countDown()
                    assertTrue(ready.await(2, TimeUnit.SECONDS))
                    assertTrue(release.await(2, TimeUnit.SECONDS))
                    block()
                }
            }
            assertTrue(ready.await(2, TimeUnit.SECONDS))
            release.countDown()
            futures.map { it.get(10, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun envelope(sequence: Long, signer: KeyPair): String {
        val payload = buildList {
            add(SignedCallRulePackageVerifier.DOMAIN)
            add("package_id=${SignedCallRulePackageVerifier.EXPECTED_PACKAGE_ID}")
            add("sequence=$sequence")
            add("issued_at_ms=${NOW - 1_000L}")
            add("expires_at_ms=${NOW + 3_600_000L}")
            add("issuer_id=sentinel-rules")
            add("key_id=trusted")
            add("silence_prefix=+33897")
        }.joinToString("\n").toByteArray(Charsets.UTF_8)
        val signature = Signature.getInstance("SHA256withECDSA").apply {
            initSign(signer.private)
            update(payload)
        }.sign()
        return "key_id=trusted\npayload_hex=${payload.hex()}\nsignature_hex=${signature.hex()}"
    }

    private fun keyPair(): KeyPair = KeyPairGenerator.getInstance("EC").run {
        initialize(ECGenParameterSpec("secp256r1"))
        generateKeyPair()
    }

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private companion object {
        const val NOW = 2_000_000_000_000L
    }
}
