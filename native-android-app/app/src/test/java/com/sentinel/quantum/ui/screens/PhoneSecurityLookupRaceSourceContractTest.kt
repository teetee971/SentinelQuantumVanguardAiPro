package com.sentinel.quantum.ui.screens

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class PhoneSecurityLookupRaceSourceContractTest {
    private val source: String by lazy {
        File("src/main/java/com/sentinel/quantum/ui/screens/PhoneSecurityScreen.kt").readText()
    }

    @Test
    fun editingNumberInvalidatesEveryLookupResult() {
        assertTrue(source.contains("lookupGeneration"), "Phone lookup state needs a generation")
        assertTrue(source.contains("checkResult = null"), "Editing must clear the local result")
        assertTrue(source.contains("explanation = null"), "Editing must clear the explanation")
        assertTrue(source.contains("monitorStats = null"), "Editing must clear monitor statistics")
        assertTrue(source.contains("remoteResult = null"), "Editing must clear remote reputation")
        assertTrue(source.contains("remoteStatus = null"), "Editing must clear remote status")
        assertTrue(source.contains("arcepResult = null"), "Editing must clear ARCEP results")
        assertTrue(source.contains("arcepStatus = null"), "Editing must clear ARCEP status")
        assertTrue(source.contains("rtrResult = null"), "Editing must clear RTR results")
        assertTrue(source.contains("actionStatus = null"), "Editing must clear action status")
        assertTrue(source.contains("directoryRunning = false"), "Editing must stop directory progress state")
    }

    @Test
    fun asynchronousLookupWritesVerifyGenerationAndCandidate() {
        assertTrue(source.contains("isCurrentLookup(generation, candidate, lookupGeneration, phoneNumber)"))
        assertTrue(
            source.contains("generationSafeReputationPersistence.invalidate()"),
            "Editing must invalidate the generation through the persistence coordinator"
        )
        assertTrue(source.contains("candidate == currentPhoneNumber"), "Async writes must verify the displayed candidate")
    }
}
