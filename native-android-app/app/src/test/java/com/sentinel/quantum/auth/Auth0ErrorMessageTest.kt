package com.sentinel.quantum.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class Auth0ErrorMessageTest {
    @Test
    fun preserves_a_non_blank_provider_error_for_diagnostics() {
        assertEquals(
            "Connexion impossible : access_denied",
            auth0ErrorMessage("access_denied")
        )
    }

    @Test
    fun uses_safe_fallback_when_provider_error_is_missing() {
        assertEquals(
            "Connexion impossible. Aucun jeton n’a été conservé.",
            auth0ErrorMessage(null)
        )
        assertEquals(
            "Connexion impossible. Aucun jeton n’a été conservé.",
            auth0ErrorMessage("  ")
        )
    }
}
