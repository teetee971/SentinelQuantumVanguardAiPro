package com.sentinel.quantum

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiProductTruthPolishContractTest {
    @Test
    fun voiceStudioSeparatesCustomerStatusFromTechnicalFailures() {
        val source = File(
            "src/main/java/com/sentinel/quantum/VoiceStudioActivity.kt"
        ).readText()

        assertTrue(source.contains("Aperçu local : disponible"))
        assertTrue(source.contains("PTT de test"))
        assertTrue(source.contains("Appels téléphoniques transformés : non disponibles"))
        assertTrue(source.contains("voiceSessionUserMessage"))
        assertFalse(
            source.contains(
                "Session vocale refusée : \${failure.message.orEmpty()}"
            )
        )
    }

    @Test
    fun settingsMasksDeviceBindingIdentifierByDefault() {
        val source = File(
            "src/main/java/com/sentinel/quantum/ui/screens/SettingsScreen.kt"
        ).readText()

        assertTrue(source.contains("showFullDeviceId"))
        assertTrue(source.contains("maskDeviceId"))
        assertTrue(source.contains("Afficher l’identifiant complet"))
        assertTrue(source.contains("Masquer l’identifiant complet"))
    }

    @Test
    fun arcepCopyDescribesLocalRegulatoryFilteringWithoutCertificationClaim() {
        val source = File(
            "src/main/java/com/sentinel/quantum/ui/screens/ArcepVerifiedPrefixSection.kt"
        ).readText()

        assertTrue(source.contains("Filtrage des plages ARCEP actif"))
        assertTrue(source.contains("plages chargées localement"))
        assertFalse(source.contains("Protection ARCEP active"))
    }

    @Test
    fun digitalExposureKeepsScrollableContentAboveSystemNavigation() {
        val source = File(
            "src/main/java/com/sentinel/quantum/ui/screens/DigitalExposureScreen.kt"
        ).readText()

        assertTrue(source.contains("navigationBarsPadding()"))
    }
}
