package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JoinedLanSightingTest {
    @Test
    fun onlyAFullFingerprintIsRemembered() {
        val fingerprint = "a".repeat(64)
        assertTrue(JoinedLanSighting.acceptable(fingerprint))
        assertFalse(JoinedLanSighting.acceptable("nomi-ipc-s21fe-b45f"))
        assertEquals("déjà annoncé sur cet appareil", JoinedLanSighting.label(alreadyKnown = true))
        assertEquals("première annonce enregistrée ici", JoinedLanSighting.label(alreadyKnown = false))
    }
}
