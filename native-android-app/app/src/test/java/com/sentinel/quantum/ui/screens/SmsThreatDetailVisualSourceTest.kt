package com.sentinel.quantum.ui.screens

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsThreatDetailVisualSourceTest {
    private val source = File(
        "src/main/java/com/sentinel/quantum/ui/screens/SmsScannerScreen.kt"
    ).readText()

    @Test
    fun threatDetailUsesOnlyAnalyzerEvidence() {
        listOf(
            "sms-threat-detail",
            "Menace détectée",
            "Niveau de risque",
            "Liens inspectés",
            "Comportement suspect",
            "Aucune action externe automatique"
        ).forEach { assertTrue("Missing threat detail marker: $it", source.contains(it)) }
        assertTrue(source.contains("analysis.findings"))
        assertTrue(source.contains("analysis.score"))
    }
}
