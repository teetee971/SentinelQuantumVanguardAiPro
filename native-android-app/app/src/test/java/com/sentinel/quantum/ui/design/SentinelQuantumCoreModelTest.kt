package com.sentinel.quantum.ui.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SentinelQuantumCoreModelTest {
    @Test
    fun moduleAccessibilityLabelsExposeStatusAndActionability() {
        val module = SentinelCoreModule(
            id = "vpn",
            label = "VPN",
            status = SentinelStatus.LIMITED,
            enabled = true
        )

        assertEquals("VPN · Limité", module.accessibilityLabel())
        assertTrue(module.isActionable)
    }

    @Test
    fun disabledModulesRemainVisibleButAreNotActionable() {
        val module = SentinelCoreModule(
            id = "community",
            label = "Communauté",
            status = SentinelStatus.UNKNOWN,
            enabled = false
        )

        assertEquals("Communauté · Inconnu · indisponible", module.accessibilityLabel())
        assertFalse(module.isActionable)
    }

    @Test
    fun evidenceEdgesRequireAProofReference() {
        val edge = SentinelEvidenceEdge(
            from = "identity",
            to = "email",
            proofReference = "local-email-check-01"
        )
        val unproven = edge.copy(proofReference = null)

        assertTrue(edge.isBackedByEvidence)
        assertFalse(unproven.isBackedByEvidence)
    }
}
