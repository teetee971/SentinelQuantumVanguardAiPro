package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallHistoryPresentationPolicyTest {
    @Test
    fun cachedNameKeepsMaskedNumberVisible() {
        val labels = CallHistoryPresentationPolicy.labels(null, "Alice")
        assertEquals("Alice", labels.primary)
        assertEquals("Numéro masqué", labels.secondary)
    }

    @Test
    fun blankNumberKeepsMaskedNumberVisible() {
        val labels = CallHistoryPresentationPolicy.labels("   ", "Alice")
        assertEquals("Alice", labels.primary)
        assertEquals("Numéro masqué", labels.secondary)
    }

    @Test
    fun unnamedMaskedCallUsesMaskedPrimaryLabel() {
        val labels = CallHistoryPresentationPolicy.labels(null, null)
        assertEquals("Numéro masqué", labels.primary)
        assertNull(labels.secondary)
    }

    @Test
    fun namedCallShowsNumberAsSecondaryLabel() {
        val labels = CallHistoryPresentationPolicy.labels("+590690000000", "Alice")
        assertEquals("Alice", labels.primary)
        assertEquals("+590690000000", labels.secondary)
    }

    @Test
    fun unnamedCallUsesNumberAsPrimaryLabel() {
        val labels = CallHistoryPresentationPolicy.labels("+590690000000", null)
        assertEquals("+590690000000", labels.primary)
        assertNull(labels.secondary)
    }
}
