package com.sentinel.quantum.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeCommandCenterSourceContractTest {
    private val source = File(
        "src/main/java/com/sentinel/quantum/ui/screens/HomeScreen.kt"
    ).readText()
    private val presentation = File(
        "src/main/java/com/sentinel/quantum/ui/presentation/HomeSecurityPresentation.kt"
    ).readText()

    @Test
    fun commandCenterContainsTheReferenceRegionsAndHonestStateCopy() {
        listOf(
            "home_command_center",
            "Protection Sentinel",
            "Actions rapides",
            "Analyse complète",
            "Contrôle des applications",
            "Réseau sécurisé"
        ).forEach { assertTrue("Missing home command marker: $it", source.contains(it)) }
        assertTrue(source.contains("état observé"))
        assertTrue(presentation.contains("aucun historique d’événements fourni à l’accueil"))
    }
}
