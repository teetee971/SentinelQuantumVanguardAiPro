package com.sentinel.quantum

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactVisualSourceContractTest {
    private val source = File(
        "src/main/java/com/sentinel/quantum/SentinelDialerActivity.kt"
    ).readText()

    @Test
    fun contactSurfaceUsesLocalCountsAndKeepsExternalProfilesUnclaimed() {
        listOf(
            "contacts-command-center",
            "Répertoire Sentinel",
            "Contacts locaux",
            "Numéros appelables",
            "Aucun profil social synchronisé"
        ).forEach { assertTrue("Missing contact marker: $it", source.contains(it)) }
        assertTrue(source.contains("contactsPermissionGranted"))
        assertTrue(source.contains("Vos contacts restent sur cet appareil."))
    }
}
