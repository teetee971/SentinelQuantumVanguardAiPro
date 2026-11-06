package com.sentinel.quantum.ui.screens

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the reference-inspired number profile without allowing fabricated reputation data. */
class NumberSearchVisualSourceContractTest {
    private val source = File(
        "src/main/java/com/sentinel/quantum/ui/screens/NumberSearchScreen.kt"
    ).readText()

    @Test
    fun profileContainsTheReferenceVisualRegions() {
        listOf(
            "number_profile_search",
            "number_profile_hero",
            "number_profile_reliability",
            "number_profile_observations",
            "Identité et fiabilité",
            "Provenance",
            "Signalements et observations",
            "Actions sur ce numéro"
        ).forEach { marker -> assertTrue("Missing profile marker: $marker", source.contains(marker)) }
    }

    @Test
    fun profileDoesNotClaimUnknownNumberEvidence() {
        assertTrue(source.contains("Aucun score ne sera calculé sans source vérifiable."))
        assertTrue(source.contains("Aucune donnée disponible"))
        assertTrue(source.contains("recherche Phone Core"))
        assertFalse(source.contains("Score indicatif\n32/100"))
        assertFalse(source.contains("Contact confirmé"))
    }
}
