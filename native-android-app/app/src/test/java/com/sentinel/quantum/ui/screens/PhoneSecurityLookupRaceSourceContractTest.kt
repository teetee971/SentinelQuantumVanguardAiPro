package com.sentinel.quantum.ui.screens

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneSecurityLookupRaceSourceContractTest {
    private val source: String by lazy {
        File("src/main/java/com/sentinel/quantum/ui/screens/PhoneSecurityScreen.kt").readText()
    }

    @Test
    fun editingNumberInvalidatesEveryLookupResult() {
        assertTrue("Phone lookup state needs a generation", source.contains("lookupGeneration"))
        assertTrue("Editing must clear the local result", source.contains("checkResult = null"))
        assertTrue("Editing must clear the explanation", source.contains("explanation = null"))
        assertTrue("Editing must clear monitor statistics", source.contains("monitorStats = null"))
        assertTrue("Editing must clear remote reputation", source.contains("remoteResult = null"))
        assertTrue("Editing must clear remote status", source.contains("remoteStatus = null"))
        assertTrue("Editing must clear ARCEP results", source.contains("arcepResult = null"))
        assertTrue("Editing must clear ARCEP status", source.contains("arcepStatus = null"))
        assertTrue("Editing must clear RTR results", source.contains("rtrResult = null"))
        assertTrue("Editing must clear action status", source.contains("actionStatus = null"))
        assertTrue("Editing must stop directory progress state", source.contains("directoryRunning = false"))
    }

    @Test
    fun asynchronousLookupWritesVerifyGenerationAndCandidate() {
        assertTrue(source.contains("isCurrentLookup(generation, candidate, lookupGeneration, phoneNumber)"))
        assertTrue(
            "Editing must invalidate the generation through the persistence coordinator",
            source.contains("generationSafeReputationPersistence.invalidate()")
        )
        assertTrue("Async writes must verify the displayed candidate", source.contains("candidate == currentPhoneNumber"))
    }
}
