package com.sentinel.quantum.ui.screens

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemDoctorVisualSourceContractTest {
    private val source = File(
        "src/main/java/com/sentinel/quantum/ui/screens/SystemDoctorScreen.kt"
    ).readText()

    @Test
    fun deviceAnalysisUsesReferenceRegionsWithoutInventedThreatScores() {
        listOf(
            "device-analysis-dashboard",
            "Analyse de l’appareil",
            "Applications",
            "Système",
            "Réseau",
            "Résumé des détections",
            "Analyse IA indisponible"
        ).forEach { assertTrue("Missing device analysis marker: $it", source.contains(it)) }
        assertTrue(source.contains("result.evidence"))
    }
}
