package com.sentinel.quantum.ui.screens

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** SDK-free guard for the Protection D.1 hierarchy; Compose instrumentation covers runtime semantics. */
class ProtectionVisualSourceContractTest {
    private val source = File(
        "src/main/java/com/sentinel/quantum/ui/screens/PhoneSecurityScreen.kt"
    ).readText()

    @Test
    fun commandCenterAndPrimaryActionsHaveStableSemanticsTags() {
        listOf(
            "protection-command-center",
            "protection-analysis-action",
            "protection-app-permissions-action",
            "protection-network-action",
            "protection-vpn-action"
        ).forEach { tag ->
            assertTrue("Missing Protection semantics tag: $tag", source.contains("testTag(\"$tag\")"))
        }
    }

    @Test
    fun primaryProtectionActionsKeepTheirRealNavigationDestinations() {
        listOf(
            "Screen.SecurityAudit.route",
            "Screen.AppPermissionAnalyzer.route",
            "Screen.NetworkSurveillance.route",
            "Screen.Vpn.route"
        ).forEach { route ->
            assertTrue("Missing Protection navigation route: $route", source.contains(route))
        }
    }

    @Test
    fun commandCenterUsesTruthfulD1HeroInsteadOfClaimingAntivirus() {
        assertTrue(source.contains("SentinelHeroVisual("))
        assertTrue(source.contains("SentinelStatus.UNKNOWN") || source.contains("SentinelStatus.LIMITED"))
        assertTrue(!source.contains("100 % protégé"))
        assertTrue(!source.contains("Scan antivirus complet"))
    }
}
