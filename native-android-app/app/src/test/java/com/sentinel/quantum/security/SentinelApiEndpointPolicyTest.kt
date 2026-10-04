package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SentinelApiEndpointPolicyTest {
    @Test
    fun buildsAllowedPublicEndpointFromHttpsRootOrigin() {
        val endpoint = SentinelApiEndpointPolicy.build(
            "https://api.example.test",
            "/v1/report-call-public"
        )

        assertEquals("https://api.example.test/v1/report-call-public", endpoint.toString())
    }

    @Test
    fun extractsValidatedOriginHost() {
        assertEquals(
            "api.example.test",
            SentinelApiEndpointPolicy.originHost("https://api.example.test/")
        )
    }

    @Test
    fun preservesExplicitHostPinning() {
        val allowed = setOf("api.example.test")
        SentinelApiEndpointPolicy.build(
            "https://api.example.test",
            "/v1/intelligence/lookup",
            allowed
        )

        assertThrows(SecurityException::class.java) {
            SentinelApiEndpointPolicy.build(
                "https://evil.example.test",
                "/v1/intelligence/lookup",
                allowed
            )
        }
    }

    @Test
    fun rejectsHttpOrigin() {
        assertThrows(SecurityException::class.java) {
            SentinelApiEndpointPolicy.build(
                "http://api.example.test",
                "/v1/evaluate-call"
            )
        }
    }

    @Test
    fun rejectsOriginWithEmbeddedPath() {
        assertThrows(SecurityException::class.java) {
            SentinelApiEndpointPolicy.build(
                "https://api.example.test/internal",
                "/v1/evaluate-call"
            )
        }
    }

    @Test
    fun rejectsServerOnlyRoute() {
        assertThrows(SecurityException::class.java) {
            SentinelApiEndpointPolicy.build(
                "https://api.example.test",
                "/v1/moderation/pending"
            )
        }
    }

    @Test
    fun rejectsCredentialsInOrigin() {
        assertThrows(SecurityException::class.java) {
            SentinelApiEndpointPolicy.build(
                "https://user:password@api.example.test",
                "/v1/intelligence/lookup"
            )
        }
    }
}
