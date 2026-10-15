package com.sentinel.quantum

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneSecurityReputationPersistenceContractTest {
    @Test fun successfulConsentedPhoneSecurityLookupFeedsSharedCommunicationCache() {
        val source = File(
            "src/main/java/com/sentinel/quantum/ui/screens/PhoneSecurityScreen.kt"
        ).readText()

        assertTrue(source.contains("CommunicationReputationWriter"))
        assertTrue(source.contains("SharedPreferencesCommunicationReputationStorage(context.applicationContext)"))
        assertTrue(source.contains(".persist(candidate, result)"))
    }

    @Test fun staleLookupIsRejectedBeforeReputationPersistence() {
        val lookupBlockStart = source.indexOf("val checked = withContext(Dispatchers.IO)")
        val lookupBlockEnd = source.indexOf("remoteStatus = \"Réputation distante reçue\"", lookupBlockStart)
        assertTrue(lookupBlockStart >= 0, "Remote lookup block must remain identifiable")
        assertTrue(lookupBlockEnd > lookupBlockStart, "Remote lookup must expose its completion state")

        val lookupBlock = source.substring(lookupBlockStart, lookupBlockEnd + "remoteStatus = \"Réputation distante reçue\"".length)
        val guardIndex = lookupBlock.indexOf("if (!isCurrentLookup(generation, candidate, lookupGeneration, phoneNumber))")
        val persistIndex = lookupBlock.indexOf(".persist(candidate, result)")
        val remoteResultIndex = lookupBlock.indexOf("remoteResult = result")
        val lastGuardIndex = lookupBlock.lastIndexOf("if (!isCurrentLookup(generation, candidate, lookupGeneration, phoneNumber))")

        assertTrue(guardIndex >= 0, "Remote lookup must guard stale generations")
        assertTrue(persistIndex >= 0, "Remote lookup must persist successful reputation")
        assertTrue(remoteResultIndex >= 0, "Remote lookup must expose its successful result")
        assertTrue(
            guardIndex < persistIndex,
            "Stale lookup must be rejected before it can persist reputation"
        )
        assertTrue(
            lastGuardIndex < remoteResultIndex,
            "Stale lookup must be rejected before it can update displayed state"
        )
    }
}
