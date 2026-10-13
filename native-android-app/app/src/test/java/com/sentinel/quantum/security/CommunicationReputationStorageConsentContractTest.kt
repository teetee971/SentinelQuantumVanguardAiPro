package com.sentinel.quantum.security

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunicationReputationStorageConsentContractTest {
    @Test fun androidStorageRechecksCurrentConsentBeforeCommit() {
        val source = File(
            "src/main/java/com/sentinel/quantum/security/SharedPreferencesCommunicationReputationStorage.kt"
        ).readText()

        assertTrue(source.contains("SettingsStore(appContext)"))
        assertTrue(source.contains("callerReputationEnrichmentEnabled"))
        assertTrue(source.contains("ProtectionModePolicy.permitsCallerNumberEnrichment"))
        assertTrue(source.contains("REMOTE_REPUTATION_PERSISTENCE_REVOKED"))
    }

    @Test fun finalConsentCheckHappensAfterKeyDerivationAndImmediatelyBeforeCommitPath() {
        val source = File(
            "src/main/java/com/sentinel/quantum/security/SharedPreferencesCommunicationReputationStorage.kt"
        ).readText()
        val keyIndex = source.indexOf("val persistedKey = storageKey(key)")
        val consentIndex = source.indexOf("requirePersistenceAllowed()", startIndex = keyIndex.coerceAtLeast(0))
        val commitIndex = source.indexOf("putString(persistedKey, value).commit()", startIndex = consentIndex.coerceAtLeast(0))

        assertTrue(keyIndex >= 0)
        assertTrue(consentIndex > keyIndex)
        assertTrue(commitIndex > consentIndex)
    }
}
