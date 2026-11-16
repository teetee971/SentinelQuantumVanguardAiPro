package com.sentinel.quantum.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AppPermissionAnalyzerTest {

    @Test
    fun sensitivePermissionsAreHighRisk() {
        assertEquals(
            PermissionRiskLevel.HIGH_RISK,
            AppPermissionAnalyzer.classifyPermission("android.permission.CAMERA")
        )
        assertEquals(
            PermissionRiskLevel.HIGH_RISK,
            AppPermissionAnalyzer.classifyPermission("android.permission.ACCESS_FINE_LOCATION")
        )
    }

    @Test
    fun connectivityPermissionsAreMediumRisk() {
        assertEquals(
            PermissionRiskLevel.MEDIUM_RISK,
            AppPermissionAnalyzer.classifyPermission("android.permission.INTERNET")
        )
        assertEquals(
            PermissionRiskLevel.MEDIUM_RISK,
            AppPermissionAnalyzer.classifyPermission("android.permission.ACCESS_NETWORK_STATE")
        )
    }

    @Test
    fun unknownPermissionsAreLowRisk() {
        assertEquals(
            PermissionRiskLevel.LOW_RISK,
            AppPermissionAnalyzer.classifyPermission("com.example.permission.INTERNAL")
        )
    }

    @Test
    fun scoreWeightsHighAsThreeAndMediumAsOne() {
        val permissions = listOf(
            PermissionRiskLabel("android.permission.CAMERA", "Camera", PermissionRiskLevel.HIGH_RISK),
            PermissionRiskLabel("android.permission.INTERNET", "Internet", PermissionRiskLevel.MEDIUM_RISK),
            PermissionRiskLabel("com.example.permission.INTERNAL", "Internal", PermissionRiskLevel.LOW_RISK)
        )

        assertEquals(4, AppPermissionAnalyzer.calculateRiskScore(permissions))
    }

    @Test
    fun overallRiskLabelsFollowScoreThresholds() {
        assertEquals(OverallPermissionRisk.LOW, AppPermissionAnalyzer.overallRiskForScore(0))
        assertEquals(OverallPermissionRisk.MEDIUM, AppPermissionAnalyzer.overallRiskForScore(1))
        assertEquals(OverallPermissionRisk.HIGH, AppPermissionAnalyzer.overallRiskForScore(6))
        assertEquals(OverallPermissionRisk.CRITICAL, AppPermissionAnalyzer.overallRiskForScore(12))
    }
}
