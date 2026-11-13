package com.sentinel.quantum.ui.screens

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneSecurityVisualDashboardSourceContractTest {
    private val source = File(
        "src/main/java/com/sentinel/quantum/ui/screens/PhoneSecurityScreen.kt"
    ).readText()

    @Test
    fun dashboardKeepsReferenceRegionsAndTruthfulStates() {
        listOf(
            "protection-command-center-dashboard",
            "Protection temps réel",
            "Phone Core",
            "Applications",
            "Réseau local",
            "VPN",
            "Alertes récentes",
            "Aucune alerte récente fournie",
            "callPrerequisitesReady",
            "messagePrerequisitesReady"
        ).forEach { assertTrue("Missing protection dashboard marker: $it", source.contains(it)) }
        assertTrue(source.contains("Passerelle non configurée"))
        assertTrue(source.contains("aucune protection globale n’est déclarée active automatiquement"))
    }
}
