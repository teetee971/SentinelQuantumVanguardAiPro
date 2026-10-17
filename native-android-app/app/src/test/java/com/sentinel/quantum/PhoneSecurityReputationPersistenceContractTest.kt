package com.sentinel.quantum

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneSecurityReputationPersistenceContractTest {
    private val source = File(
        "src/main/java/com/sentinel/quantum/ui/screens/PhoneSecurityScreen.kt"
    ).readText()

    @Test fun successfulConsentedPhoneSecurityLookupFeedsSharedCommunicationCache() {
        assertTrue(source.contains("CommunicationReputationWriter"))
        assertTrue(source.contains("SharedPreferencesCommunicationReputationStorage(context.applicationContext)"))
        assertTrue(source.contains("GenerationSafeReputationPersistence"))
        assertTrue(source.contains("persistIfCurrent"))
    }

    @Test fun staleLookupIsRejectedBeforeReputationPersistence() {
        val lookupBlockStart = source.indexOf("val checked = withContext(Dispatchers.IO)")
        val lookupBlockEnd = source.indexOf("remoteStatus = \"Réputation distante reçue\"", lookupBlockStart)
        assertTrue("Remote lookup block must remain identifiable", lookupBlockStart >= 0)
        assertTrue("Remote lookup must expose its completion state", lookupBlockEnd > lookupBlockStart)

        val lookupBlock = source.substring(lookupBlockStart, lookupBlockEnd + "remoteStatus = \"Réputation distante reçue\"".length)
        val guardIndex = lookupBlock.indexOf("if (!isCurrentLookup(generation, candidate, lookupGeneration, phoneNumber))")
        val persistIndex = lookupBlock.indexOf("persistIfCurrent")
        val remoteResultIndex = lookupBlock.indexOf("remoteResult = result")
        val lastGuardIndex = lookupBlock.lastIndexOf("if (!isCurrentLookup(generation, candidate, lookupGeneration, phoneNumber))")

        assertTrue("Remote lookup must guard stale generations", guardIndex >= 0)
        assertTrue("Remote lookup must persist successful reputation", persistIndex >= 0)
        assertTrue("Remote lookup must expose its successful result", remoteResultIndex >= 0)
        assertTrue(
            "Stale lookup must be rejected before it can persist reputation",
            guardIndex < persistIndex
        )
        assertTrue(
            "Stale lookup must be rejected before it can update displayed state",
            lastGuardIndex < remoteResultIndex
        )
    }
}
