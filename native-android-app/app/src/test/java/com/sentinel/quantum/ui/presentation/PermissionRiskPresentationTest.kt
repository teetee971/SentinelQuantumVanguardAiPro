package com.sentinel.quantum.ui.presentation

import com.sentinel.quantum.data.OverallPermissionRisk
import com.sentinel.quantum.ui.design.SentinelStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class PermissionRiskPresentationTest {

    @Test
    fun permissionHeuristicNeverSynthesizesMalwareBadStatus() {
        assertEquals(SentinelStatus.WARN, PermissionRiskPresentation.status(OverallPermissionRisk.CRITICAL))
        assertEquals(SentinelStatus.WARN, PermissionRiskPresentation.status(OverallPermissionRisk.HIGH))
        assertEquals(SentinelStatus.WARN, PermissionRiskPresentation.status(OverallPermissionRisk.MEDIUM))
        assertEquals(SentinelStatus.UNKNOWN, PermissionRiskPresentation.status(OverallPermissionRisk.LOW))
    }

    @Test
    fun permissionHeuristicUsesExplicitFrenchLabels() {
        assertEquals("Heuristique critique", PermissionRiskPresentation.label(OverallPermissionRisk.CRITICAL))
        assertEquals("Risque de permissions élevé", PermissionRiskPresentation.label(OverallPermissionRisk.HIGH))
        assertEquals("Risque de permissions moyen", PermissionRiskPresentation.label(OverallPermissionRisk.MEDIUM))
        assertEquals("Signal faible / non concluant", PermissionRiskPresentation.label(OverallPermissionRisk.LOW))
    }
}
