package com.sentinel.quantum.ui.screens

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** SDK-free guard for the D.1 digital-footprint surface and its product-truth boundaries. */
class DigitalExposureVisualSourceContractTest {
    private val source = File(
        "src/main/java/com/sentinel/quantum/ui/screens/DigitalExposureScreen.kt"
    ).readText()

    @Test
    fun digitalFootprintUsesStableD1Semantics() {
        listOf(
            "exposure-command-center",
            "exposure-password-check",
            "exposure-email-watch"
        ).forEach { tag ->
            assertTrue("Missing Digital Exposure semantics tag: $tag", source.contains("testTag(\"$tag\")"))
        }
        assertTrue(source.contains("SentinelHeroVisual("))
    }

    @Test
    fun passwordExposureKeepsRealKAnonymousCapability() {
        assertTrue(source.contains("PwnedPasswordClient"))
        assertTrue(source.contains("5 premiers caractères du SHA-1"))
        assertTrue(source.contains("Le mot de passe et son hash complet ne sont jamais envoyés"))
    }

    @Test
    fun unavailableEmailMonitoringStaysExplicitlyLocked() {
        assertTrue(source.contains("SentinelStatus.LOCKED"))
        assertTrue(source.contains("fournisseur autorisé") || source.contains("backend autorisé"))
        assertTrue(!source.contains("dark web analysé"))
        assertTrue(!source.contains("adresse e-mail scannée"))
        assertTrue(!source.contains("aucune fuite détectée"))
        assertTrue(!source.contains("protection identité active"))
    }
}
