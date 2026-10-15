package com.sentinel.quantum.ui.presentation

import com.sentinel.quantum.ui.design.SentinelStatus

/**
 * Truthful presentation state for the device-analysis surfaces.
 *
 * This layer does not perform malware detection. It only translates evidence already produced by
 * implemented local checks into explicit UI states.
 */
enum class AnalysisLayerState {
    COMPLETE,
    RUNNING,
    NOT_EXECUTED,
    UNAVAILABLE,
    FAILED
}

data class DeviceAnalysisLayerFacts(
    val implemented: Boolean = true,
    val running: Boolean = false,
    val completed: Boolean = false,
    val failed: Boolean = false,
    val healthyEvidence: Boolean? = null,
    val findingCount: Int? = null,
    val criticalFindingCount: Int = 0
)

data class DeviceAnalysisFacts(
    val installation: DeviceAnalysisLayerFacts,
    val permissions: DeviceAnalysisLayerFacts,
    val applications: DeviceAnalysisLayerFacts,
    /**
     * Informational only. A count of sensitive permissions is never converted into a threat count.
     */
    val sensitivePermissionCount: Int = 0
)

data class DeviceAnalysisLayerUiState(
    val state: AnalysisLayerState,
    val healthyEvidence: Boolean?,
    val findingCount: Int?,
    val criticalFindingCount: Int
)

data class DeviceAnalysisUiState(
    val installation: DeviceAnalysisLayerUiState,
    val permissions: DeviceAnalysisLayerUiState,
    val applications: DeviceAnalysisLayerUiState,
    val overallStatus: SentinelStatus,
    /** Null means that the available layers do not provide a complete explicit finding count. */
    val explicitFindingCount: Int?
)

object DeviceAnalysisPresentation {
    fun from(facts: DeviceAnalysisFacts): DeviceAnalysisUiState {
        val installation = facts.installation.toUiState()
        val permissions = facts.permissions.toUiState()
        val applications = facts.applications.toUiState()
        val layers = listOf(installation, permissions, applications)
        val requiredLayers = layers.filter { it.state != AnalysisLayerState.UNAVAILABLE }

        val explicitFindingCount = explicitFindingCount(requiredLayers)
        val overallStatus = overallStatus(requiredLayers)

        return DeviceAnalysisUiState(
            installation = installation,
            permissions = permissions,
            applications = applications,
            overallStatus = overallStatus,
            explicitFindingCount = explicitFindingCount
        )
    }

    private fun DeviceAnalysisLayerFacts.toUiState(): DeviceAnalysisLayerUiState {
        val state = when {
            !implemented -> AnalysisLayerState.UNAVAILABLE
            failed -> AnalysisLayerState.FAILED
            running -> AnalysisLayerState.RUNNING
            completed -> AnalysisLayerState.COMPLETE
            else -> AnalysisLayerState.NOT_EXECUTED
        }

        return DeviceAnalysisLayerUiState(
            state = state,
            healthyEvidence = healthyEvidence,
            findingCount = findingCount?.coerceAtLeast(0),
            criticalFindingCount = criticalFindingCount.coerceAtLeast(0)
        )
    }

    private fun explicitFindingCount(requiredLayers: List<DeviceAnalysisLayerUiState>): Int? {
        if (requiredLayers.isEmpty()) return null
        if (requiredLayers.any { it.state != AnalysisLayerState.COMPLETE }) return null
        val counts = requiredLayers.map { it.findingCount ?: return null }
        return counts.sum()
    }

    private fun overallStatus(requiredLayers: List<DeviceAnalysisLayerUiState>): SentinelStatus {
        if (requiredLayers.isEmpty()) return SentinelStatus.UNKNOWN

        if (requiredLayers.any { it.criticalFindingCount > 0 }) {
            return SentinelStatus.BAD
        }

        if (requiredLayers.any {
                it.state == AnalysisLayerState.FAILED ||
                    it.healthyEvidence == false ||
                    (it.findingCount ?: 0) > 0
            }
        ) {
            return SentinelStatus.WARN
        }

        if (requiredLayers.all { it.state == AnalysisLayerState.NOT_EXECUTED }) {
            return SentinelStatus.UNKNOWN
        }

        if (requiredLayers.any {
                it.state != AnalysisLayerState.COMPLETE || it.healthyEvidence != true
            }
        ) {
            return SentinelStatus.LIMITED
        }

        return SentinelStatus.OK
    }
}
