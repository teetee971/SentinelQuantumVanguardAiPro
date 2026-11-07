package com.sentinel.quantum.ui.screens

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnVisualDashboardSourceContractTest {
    private val source = File(
        "src/main/java/com/sentinel/quantum/ui/screens/VpnScreen.kt"
    ).readText()

    @Test
    fun vpnDashboardUsesReferenceRegionsWithoutFabricatedTunnelEvidence() {
        listOf(
            "vpn-reference-dashboard",
            "Serveur VPN",
            "IP de sortie",
            "Latence",
            "Données chiffrées",
            "Fonctionnalités de protection",
            "Actions rapides"
        ).forEach { assertTrue("Missing VPN marker: $it", source.contains(it)) }
        assertTrue(source.contains("Non attestée"))
        assertTrue(source.contains("READY_NO_GATEWAY"))
    }
}
