package com.sentinel.quantum.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DigitalExposureClientTest {
    @Test
    fun userExposureRouteIsAuthenticatedOnly() {
        assertThrows(SecurityException::class.java) {
            SentinelApiEndpointPolicy.build(
                "https://sentinel-moteur-api.onrender.com",
                "/v1/intelligence/exposures/lookup-user",
                setOf("sentinel-moteur-api.onrender.com")
            )
        }

        val endpoint = SentinelApiEndpointPolicy.buildAuthenticated(
            "https://sentinel-moteur-api.onrender.com",
            "/v1/intelligence/exposures/lookup-user",
            setOf("sentinel-moteur-api.onrender.com")
        )
        assertTrue(endpoint.toString().endsWith("/v1/intelligence/exposures/lookup-user"))
    }

    @Test
    fun exposureEgressRequiresEnhancedModeAndExplicitConsent() {
        assertThrows(SecurityException::class.java) {
            DigitalExposureClient.requireEgressAllowed(
                PhonePrivacyFirewall.Mode.LOCAL_ONLY,
                explicitConsent = true
            )
        }
        assertThrows(SecurityException::class.java) {
            DigitalExposureClient.requireEgressAllowed(
                PhonePrivacyFirewall.Mode.ENHANCED,
                explicitConsent = false
            )
        }
        DigitalExposureClient.requireEgressAllowed(
            PhonePrivacyFirewall.Mode.ENHANCED,
            explicitConsent = true
        )
    }

    @Test
    fun lookupBodyNeverCarriesServerSubjectToken() {
        val body = DigitalExposureClient.buildLookupBody(
            listOf(DigitalExposureClient.Indicator("DOMAIN", "example.com"))
        )
        assertTrue(body.contains("example.com"))
        assertFalse(body.contains("subject_token"))
        assertFalse(body.contains("X-Exposure-Key"))
    }

    @Test
    fun bearerTokenMustBePresentButIsNeverSerializedIntoBody() {
        assertThrows(IllegalArgumentException::class.java) {
            DigitalExposureClient.bearerHeader("   ")
        }
        assertTrue(DigitalExposureClient.bearerHeader("token-value").startsWith("Bearer "))
    }
}
