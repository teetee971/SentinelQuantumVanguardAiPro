package com.sentinel.quantum.intelligence

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InCallIntelligenceUiIntegrationTest {
    @Test
    fun `in call UI reads intelligence snapshot off main thread for presentation`() {
        val source = inCallSource()

        assertTrue(source.contains("IntelligenceCoreRuntime.snapshotForNumber"))
        assertTrue(source.contains("withContext(Dispatchers.IO)"))
        assertTrue(source.contains("IntelligenceSnapshotPresentation.from"))
        assertTrue(source.contains("intelligencePresentation = intelligencePresentation"))
    }

    @Test
    fun `in call UI exposes bounded intelligence diagnostics without enforcement authority`() {
        val source = inCallSource()

        assertTrue(source.contains("Données Sentinel"))
        assertTrue(source.contains("Données insuffisantes"))
        assertTrue(source.contains("Fraîcheur"))
        assertTrue(source.contains("Preuves actives"))
        assertFalse(source.contains("ScreeningProjection.from"))
        assertFalse(source.contains("enforcementAuthorized"))
    }

    @Test
    fun `ended call renders useful session summary and caller card action`() {
        val source = inCallSource()

        assertTrue(source.contains("Résumé de l’appel"))
        assertTrue(source.contains("Durée"))
        assertTrue(source.contains("Fiche du numéro"))
        assertTrue(source.contains("CallerIdActivity::class.java"))
    }

    private fun inCallSource(): String {
        val packagePath = "com/sentinel/quantum/SentinelInCallActivity.kt"
        val candidates = listOf(
            File("src/main/java/$packagePath"),
            File("app/src/main/java/$packagePath"),
            File("native-android-app/app/src/main/java/$packagePath")
        )
        val source = candidates.firstOrNull(File::isFile)
            ?: error(
                "SentinelInCallActivity.kt not found from ${File(".").absolutePath}; " +
                    "checked ${candidates.joinToString { it.path }}"
            )
        return source.readText()
    }
}
