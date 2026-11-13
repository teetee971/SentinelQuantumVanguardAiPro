package com.sentinel.quantum.ui.screens

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPermissionVisualDashboardSourceTest {
    private val source = File(
        "src/main/java/com/sentinel/quantum/ui/screens/AppPermissionAnalyzerScreen.kt"
    ).readText()

    @Test
    fun dashboardKeepsTheReferenceRegionsAndTruthBoundary() {
        listOf(
            "applications-risk-dashboard",
            "Applications sous surveillance",
            "Applications installées",
            "Autorisations sensibles",
            "Score de comportement des apps",
            "heuristique de permissions"
        ).forEach { assertTrue("Missing dashboard marker: $it", source.contains(it)) }
        assertTrue(source.contains("ne constitue pas un scan antivirus"))
    }
}
