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
}
