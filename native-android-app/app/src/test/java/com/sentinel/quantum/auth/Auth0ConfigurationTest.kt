package com.sentinel.quantum.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Auth0ConfigurationTest {
    @Test
    fun acceptsTheConfiguredEuropeanNativeApplication() {
        val result = Auth0Configuration(
            domain = "sentinel-voice.eu.auth0.com",
            clientId = "client-id",
            scheme = "com.sentinel.quantum",
            audience = "https://sentinel-voice-api.onrender.com"
        ).validate()

        assertTrue(result.isEmpty())
    }

    @Test
    fun rejectsAnInsecureOrMismatchedConfiguration() {
        val result = Auth0Configuration(
            domain = "https://sentinel-voice.eu.auth0.com",
            clientId = "",
            scheme = "wrong.scheme",
            audience = "http://example.test"
        ).validate()

        assertFalse(result.isEmpty())
    }
}
