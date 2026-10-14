package com.sentinel.quantum.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeVisualSourceContractTest {
    private val source = File(
        "src/main/java/com/sentinel/quantum/ui/screens/HomeScreen.kt"
    ).readText()

    @Test
    fun homeWiresPremiumCommandCenterAndStableSemantics() {
        listOf(
            "HomeSecurityPresentation",
            "SentinelHeroVisual",
            "home_security_hero",
            "home_module_phone_core",
            "home_module_vpn",
            "home_module_network",
            "home_module_collective_defense",
            "home_primary_action"
        ).forEach { marker ->
            assertTrue("HomeScreen missing $marker", source.contains(marker))
        }
    }

    @Test
    fun homeNoLongerPromotesSoftwarePrerequisitesAsGreenProtection() {
        assertTrue(
            "Legacy Prérequis prêts hero must be removed",
            !source.contains("\"Prérequis prêts\"")
        )
    }
}
