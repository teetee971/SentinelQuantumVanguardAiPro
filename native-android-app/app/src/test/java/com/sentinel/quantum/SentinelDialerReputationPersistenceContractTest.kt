package com.sentinel.quantum

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SentinelDialerReputationPersistenceContractTest {
    @Test fun successfulConsentedDialerLookupFeedsTheSharedCommunicationCache() {
        val source = File(
            "src/main/java/com/sentinel/quantum/SentinelDialerActivity.kt"
        ).readText()

        assertTrue(source.contains("CommunicationReputationWriter"))
        assertTrue(source.contains("SharedPreferencesCommunicationReputationStorage(applicationContext)"))
        assertTrue(source.contains(".persist(lookupNumber, r"))
    }
}
