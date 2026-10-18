package com.sentinel.quantum.ui.screens

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialD1ReferenceRoutesSourceContractTest {
    private val screenSource = File(
        "src/main/java/com/sentinel/quantum/navigation/Screen.kt"
    ).readText()
    private val graphSource = File(
        "src/main/java/com/sentinel/quantum/navigation/NavGraph.kt"
    ).readText()

    private fun source(name: String): String = File(
        "src/main/java/com/sentinel/quantum/ui/screens/$name.kt"
    ).takeIf(File::exists)?.readText().orEmpty()

    @Test
    fun officialSurfacesHaveDedicatedRoutesAndBackNavigation() {
        listOf("ThreatMap", "Quarantine", "ThreatDetail").forEach { route ->
            assertTrue("Missing route object $route", screenSource.contains("object $route"))
            assertTrue("Missing NavGraph registration for $route", graphSource.contains("Screen.$route.route"))
        }
        assertTrue(source("ThreatMapScreen").contains("navController.popBackStack()"))
        assertTrue(source("QuarantineScreen").contains("navController.popBackStack()"))
        assertTrue(source("ThreatDetailScreen").contains("navController.popBackStack()"))
    }

    @Test
    fun threatMapKeepsMissingGeographicEvidenceUnknown() {
        val source = source("ThreatMapScreen")
        assertTrue(source.contains("SentinelSceneKind.THREAT_GLOBE"))
        assertTrue(source.contains("SentinelStatus.UNKNOWN"))
        assertTrue(source.contains("aucune preuve géographique") || source.contains("géographique"))
        assertTrue(!source.contains("2 487 311"))
    }

    @Test
    fun quarantineNeverClaimsIsolationWithoutAnObservedMechanism() {
        val source = source("QuarantineScreen")
        assertTrue(source.contains("SentinelSceneKind.QUARANTINE_CHAMBER"))
        assertTrue(source.contains("SentinelStatus.LOCKED") || source.contains("SentinelStatus.UNKNOWN"))
        assertTrue(source.contains("confinement") || source.contains("quarantaine"))
        assertTrue(!source.contains("Menaces isolées"))
    }

    @Test
    fun threatDetailKeepsCriticalRedForObservedFindingsOnly() {
        val source = source("ThreatDetailScreen")
        assertTrue(source.contains("SentinelSceneKind.THREAT_DETAIL"))
        assertTrue(source.contains("SentinelStatus.UNKNOWN"))
        assertTrue(source.contains("SentinelStatus.BAD"))
        assertTrue(source.contains("preuve") || source.contains("preuve"))
    }
}
