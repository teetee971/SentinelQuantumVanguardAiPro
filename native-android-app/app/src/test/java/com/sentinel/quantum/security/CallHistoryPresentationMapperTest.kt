package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallHistoryPresentationMapperTest {
    @Test fun projectionDoesNotExposeFingerprintValue() {
        val secret = "device-bound-secret-fingerprint"
        val model = CallHistoryPresentationMapper.from(
            CallFilterDecisionEntity(
                id = 9,
                occurredAtMs = 1000L,
                action = "BLOCK",
                reason = "USER_EXACT_BLOCK",
                source = "USER",
                numberFingerprint = secret
            )
        )
        assertTrue(model.hasPrivateIdentifier)
        assertFalse(model.toString().contains(secret))
        assertEquals("Numéro placé dans la liste de blocage", model.reason)
    }

    @Test fun allPresentationTokensAreSanitizedBeforeFrenchMapping() {
        val model = CallHistoryPresentationMapper.from(
            CallFilterDecisionEntity(
                occurredAtMs = 1000L,
                action = "ALLOW|raw\n",
                reason = "bad|reason\nraw",
                source = "NONE|raw",
                numberFingerprint = null
            )
        )
        assertEquals("État non traduit", model.action)
        assertEquals("Motif technique non traduit", model.reason)
        assertEquals("Source technique non traduite", model.source)
        assertFalse(model.hasPrivateIdentifier)
    }

    @Test fun commonAllowDecisionIsFullyFrench() {
        val model = CallHistoryPresentationMapper.from(
            CallFilterDecisionEntity(
                occurredAtMs = 1000L,
                action = "ALLOW",
                reason = "NO_MATCHING_RULE",
                source = "NONE",
                numberFingerprint = "fingerprint"
            )
        )
        assertEquals("Autorisé", model.action)
        assertEquals("Aucune règle de blocage correspondante", model.reason)
        assertEquals("Aucune règle spécifique", model.source)
    }
}
