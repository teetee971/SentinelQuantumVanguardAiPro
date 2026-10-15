package com.sentinel.quantum.ui.presentation

import com.sentinel.quantum.ui.design.SentinelStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceAnalysisPresentationTest {

    @Test
    fun missingLayersRemainNotExecutedOrUnavailable() {
        val state = DeviceAnalysisPresentation.from(
            DeviceAnalysisFacts(
                installation = DeviceAnalysisLayerFacts(),
                permissions = DeviceAnalysisLayerFacts(implemented = false),
                applications = DeviceAnalysisLayerFacts()
            )
        )

        assertEquals(AnalysisLayerState.NOT_EXECUTED, state.installation.state)
        assertEquals(AnalysisLayerState.UNAVAILABLE, state.permissions.state)
        assertEquals(AnalysisLayerState.NOT_EXECUTED, state.applications.state)
        assertEquals(SentinelStatus.UNKNOWN, state.overallStatus)
    }

    @Test
    fun sensitivePermissionCountNeverSynthesizesAThreatOrFinding() {
        val state = DeviceAnalysisPresentation.from(
            DeviceAnalysisFacts(
                installation = DeviceAnalysisLayerFacts(
                    completed = true,
                    healthyEvidence = true,
                    findingCount = 0
                ),
                permissions = DeviceAnalysisLayerFacts(
                    completed = true,
                    healthyEvidence = true,
                    findingCount = null
                ),
                applications = DeviceAnalysisLayerFacts(
                    completed = true,
                    healthyEvidence = true,
                    findingCount = 0
                ),
                sensitivePermissionCount = 18
            )
        )

        assertNull(state.explicitFindingCount)
        assertEquals(SentinelStatus.OK, state.overallStatus)
    }

    @Test
    fun overallOkRequiresExplicitHealthyEvidenceFromEveryRequiredLayer() {
        val state = DeviceAnalysisPresentation.from(
            DeviceAnalysisFacts(
                installation = DeviceAnalysisLayerFacts(
                    completed = true,
                    healthyEvidence = true,
                    findingCount = 0
                ),
                permissions = DeviceAnalysisLayerFacts(
                    completed = true,
                    healthyEvidence = null,
                    findingCount = 0
                ),
                applications = DeviceAnalysisLayerFacts(
                    completed = true,
                    healthyEvidence = true,
                    findingCount = 0
                )
            )
        )

        assertEquals(SentinelStatus.LIMITED, state.overallStatus)
    }

    @Test
    fun runningAndFailedLayersRemainExplicit() {
        val state = DeviceAnalysisPresentation.from(
            DeviceAnalysisFacts(
                installation = DeviceAnalysisLayerFacts(running = true),
                permissions = DeviceAnalysisLayerFacts(failed = true),
                applications = DeviceAnalysisLayerFacts()
            )
        )

        assertEquals(AnalysisLayerState.RUNNING, state.installation.state)
        assertEquals(AnalysisLayerState.FAILED, state.permissions.state)
        assertEquals(AnalysisLayerState.NOT_EXECUTED, state.applications.state)
        assertEquals(SentinelStatus.WARN, state.overallStatus)
    }

    @Test
    fun explicitCriticalFindingProducesBadWithoutCallingPermissionRiskMalware() {
        val state = DeviceAnalysisPresentation.from(
            DeviceAnalysisFacts(
                installation = DeviceAnalysisLayerFacts(
                    completed = true,
                    healthyEvidence = true,
                    findingCount = 0
                ),
                permissions = DeviceAnalysisLayerFacts(
                    completed = true,
                    healthyEvidence = false,
                    findingCount = 2,
                    criticalFindingCount = 1
                ),
                applications = DeviceAnalysisLayerFacts(
                    completed = true,
                    healthyEvidence = true,
                    findingCount = 0
                ),
                sensitivePermissionCount = 7
            )
        )

        assertEquals(2, state.explicitFindingCount)
        assertEquals(SentinelStatus.BAD, state.overallStatus)
    }
}
