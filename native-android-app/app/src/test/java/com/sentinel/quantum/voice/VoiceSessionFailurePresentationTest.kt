package com.sentinel.quantum.voice

import org.junit.Assert.*
import org.junit.Test

class VoiceSessionFailurePresentationTest {
    @Test fun serviceUnavailableDoesNotClaimAnActiveSession() {
        val text = VoiceSessionFailurePresentation.message(IllegalStateException("VOICE_SESSION_HTTP_503"))
        assertTrue(text.contains("indisponible"))
        assertTrue(text.contains("503"))
        assertFalse(text.contains("connectée"))
    }
    @Test fun unauthorizedAndForbiddenRemainDistinctFromBackendReadiness() {
        assertTrue(VoiceSessionFailurePresentation.message(IllegalStateException("VOICE_SESSION_HTTP_401")).contains("authentification"))
        assertTrue(VoiceSessionFailurePresentation.message(IllegalStateException("VOICE_SESSION_HTTP_403")).contains("autorisé"))
    }
    @Test fun arbitraryServerTextCannotLeakTokensOrRawErrorsToTheUi() {
        assertFalse(VoiceSessionFailurePresentation.message(IllegalStateException("secret-token-123")).contains("secret-token"))
    }
}
