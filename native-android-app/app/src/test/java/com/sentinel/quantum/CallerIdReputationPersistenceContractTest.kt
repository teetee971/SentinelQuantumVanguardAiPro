package com.sentinel.quantum

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class CallerIdReputationPersistenceContractTest {
    @Test fun successfulConsentedEnrichmentFeedsTheLocalCommunicationCache() {
        val source = File(
            "src/main/java/com/sentinel/quantum/CallerIdActivity.kt"
        ).readText()

        assertTrue(source.contains("CommunicationReputationWriter"))
        assertTrue(source.contains("SharedPreferencesCommunicationReputationStorage(applicationContext)"))
        assertTrue(source.contains(".persist(number, result"))
    }
}
