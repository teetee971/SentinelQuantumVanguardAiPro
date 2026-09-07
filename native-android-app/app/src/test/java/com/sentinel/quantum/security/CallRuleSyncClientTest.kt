package com.sentinel.quantum.security

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallRuleSyncClientTest {
    @Test
    fun `successful fetch is passed to signed installer`() {
        var installedEnvelope: String? = null
        val transport = object : CallRulePackageTransport {
            override fun fetch() = CallRulePackageTransport.FetchResult(true, "SYNC_FETCH_OK", "signed-envelope")
        }
        val installer = SignedRuleInstaller { envelope, _ ->
            installedEnvelope = envelope
            SignedCallRulePackageVerifier.Result(
                true,
                "SIGNED_RULE_ACCEPTED",
                SignedCallRulePackageVerifier.RulePackage(
                    packageId = "fr-vigilance",
                    sequence = 8L,
                    issuedAtMs = 1_000L,
                    expiresAtMs = 10_000L,
                    issuerId = "issuer",
                    keyId = "key-1",
                    silencePrefixes = setOf("+33187")
                )
            )
        }

        val result = CallRuleSyncClient(transport, installer).synchronize(2_000L)

        assertTrue(result.accepted)
        assertEquals("SYNC_RULES_INSTALLED", result.reason)
        assertEquals(8L, result.sequence)
        assertEquals("signed-envelope", installedEnvelope)
    }

    @Test
    fun `transport failure never reaches installer`() {
        var installCalled = false
        val transport = object : CallRulePackageTransport {
            override fun fetch() = CallRulePackageTransport.FetchResult(false, "SYNC_NETWORK_ERROR")
        }
        val installer = SignedRuleInstaller { _, _ ->
            installCalled = true
            SignedCallRulePackageVerifier.Result(true, "unexpected")
        }

        val result = CallRuleSyncClient(transport, installer).synchronize(2_000L)

        assertFalse(result.accepted)
        assertEquals("SYNC_NETWORK_ERROR", result.reason)
        assertFalse(installCalled)
    }

    @Test
    fun `signed verifier rejection is preserved by sync client`() {
        val transport = object : CallRulePackageTransport {
            override fun fetch() = CallRulePackageTransport.FetchResult(true, "SYNC_FETCH_OK", "replayed-envelope")
        }
        val installer = SignedRuleInstaller { _, _ ->
            SignedCallRulePackageVerifier.Result(false, "SIGNED_RULE_ROLLBACK_REJECTED")
        }

        val result = CallRuleSyncClient(transport, installer).synchronize(2_000L)

        assertFalse(result.accepted)
        assertEquals("SIGNED_RULE_ROLLBACK_REJECTED", result.reason)
    }

    @Test
    fun `transport requires explicit allowed https origin and redirects disabled`() {
        val secureClient = OkHttpCallRulePackageTransport.defaultClient()
        OkHttpCallRulePackageTransport(
            endpoint = "https://updates.example.test/call-rules.txt",
            allowedHosts = setOf("updates.example.test"),
            client = secureClient
        )

        assertFails("cleartext endpoint must fail") {
            OkHttpCallRulePackageTransport(
                endpoint = "http://updates.example.test/call-rules.txt",
                allowedHosts = setOf("updates.example.test"),
                client = secureClient
            )
        }
        assertFails("unlisted host must fail") {
            OkHttpCallRulePackageTransport(
                endpoint = "https://other.example.test/call-rules.txt",
                allowedHosts = setOf("updates.example.test"),
                client = secureClient
            )
        }
        assertFails("redirect-enabled client must fail") {
            OkHttpCallRulePackageTransport(
                endpoint = "https://updates.example.test/call-rules.txt",
                allowedHosts = setOf("updates.example.test"),
                client = OkHttpClient.Builder().followRedirects(true).build()
            )
        }
    }

    private fun assertFails(message: String, block: () -> Unit) {
        var failed = false
        try {
            block()
        } catch (_: IllegalArgumentException) {
            failed = true
        }
        assertTrue(message, failed)
    }
}
