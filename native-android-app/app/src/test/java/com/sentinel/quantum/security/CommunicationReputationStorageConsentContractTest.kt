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
}
