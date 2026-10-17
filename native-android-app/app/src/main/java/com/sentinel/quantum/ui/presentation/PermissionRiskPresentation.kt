package com.sentinel.quantum.ui.presentation

import com.sentinel.quantum.data.OverallPermissionRisk
import com.sentinel.quantum.ui.design.SentinelStatus

/**
 * Presentation-only mapping for permission heuristics.
 *
 * These levels are not malware detections. Even the source model's CRITICAL value is therefore
 * presented as attention (WARN), never as a confirmed-danger BAD status. LOW also remains
 * UNKNOWN because a weak permission signal is not proof that an app or device is safe.
 */
object PermissionRiskPresentation {
    fun status(risk: OverallPermissionRisk): SentinelStatus = when (risk) {
        OverallPermissionRisk.CRITICAL,
        OverallPermissionRisk.HIGH,
        OverallPermissionRisk.MEDIUM -> SentinelStatus.WARN
        OverallPermissionRisk.LOW -> SentinelStatus.UNKNOWN
    }

    fun label(risk: OverallPermissionRisk): String = when (risk) {
        OverallPermissionRisk.CRITICAL -> "Heuristique critique"
        OverallPermissionRisk.HIGH -> "Risque de permissions élevé"
        OverallPermissionRisk.MEDIUM -> "Risque de permissions moyen"
        OverallPermissionRisk.LOW -> "Signal faible / non concluant"
    }
}
