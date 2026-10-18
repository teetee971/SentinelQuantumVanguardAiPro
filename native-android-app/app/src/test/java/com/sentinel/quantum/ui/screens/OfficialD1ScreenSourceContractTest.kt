package com.sentinel.quantum.ui.screens

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the one-to-one mapping between official references and specialized native scenes. */
class OfficialD1ScreenSourceContractTest {
    private fun screen(name: String): String = File(
        "src/main/java/com/sentinel/quantum/ui/screens/$name.kt"
    ).readText()

    @Test
    fun commandCenterUsesTheOfficialCommandScene() {
        val source = screen("HomeScreen")
        assertTrue(source.contains("SentinelHolographicScene("))
        assertTrue(source.contains("SentinelSceneKind.COMMAND_CENTER"))
        assertTrue(source.contains("home_security_hero"))
    }

    @Test
    fun protectionUsesTheDedicatedProtectionSphere() {
        val source = screen("PhoneSecurityScreen")
        assertTrue(source.contains("SentinelHolographicScene("))
        assertTrue(source.contains("SentinelSceneKind.REAL_TIME_PROTECTION"))
        assertTrue(source.contains("protection-command-center"))
    }

    @Test
    fun riskApplicationsUsesTheDedicatedRiskCube() {
        val source = screen("AppPermissionAnalyzerScreen")
        assertTrue(source.contains("SentinelHolographicScene("))
        assertTrue(source.contains("SentinelSceneKind.RISK_APPLICATIONS"))
    }

    @Test
    fun reportAndDeviceAnalysisUseLayeredDeviceScenes() {
        val report = screen("SecurityAuditScreen")
        val device = screen("SystemDoctorScreen")
        assertTrue(report.contains("SentinelSceneKind.ANALYSIS_REPORT"))
        assertTrue(device.contains("SentinelSceneKind.DEVICE_ANALYSIS"))
    }

    @Test
    fun vpnUsesTheDedicatedVpnGlobe() {
        val source = screen("VpnScreen")
        assertTrue(source.contains("SentinelHolographicScene("))
        assertTrue(source.contains("SentinelSceneKind.VPN_GLOBE"))
    }
}
