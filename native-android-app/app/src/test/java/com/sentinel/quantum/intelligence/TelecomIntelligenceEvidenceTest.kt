package com.sentinel.quantum.intelligence

import com.sentinel.quantum.security.InternationalNumberResolver
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelecomIntelligenceEvidenceTest {
    @Test
    fun enrichmentIsAnnotationOnlyAndContainsNoRawNumber() {
        val resolution = InternationalNumberResolver().resolve("+1 202 555 0123")
        val evidence = TelecomIntelligenceEvidenceAdapter.from(
            resolution = resolution,
            sourceIds = listOf("ITU", "FCC"),
            freshness = IntelligenceEvidence.TelecomFreshness.FRESH
        )

        assertEquals("AMBIGUOUS", evidence.resolutionStatus)
        assertEquals("+1", evidence.callingCode)
        assertEquals(
            IntelligenceEvidence.TelecomAuthority.ANNOTATION_ONLY,
            evidence.authority
        )
        assertTrue(evidence.jurisdictions.any { it.contains("NANP") })
        assertFalse(evidence.toString().contains("2025550123"))
    }

    @Test
    fun telecomEvidenceRoundTripsInTheExistingPrivacyBoundedStore() {
        val directory = Files.createTempDirectory("sentinel-telecom-evidence").toFile()
        val identity = SentinelIdentityKey.phoneFingerprint("fp-telecom", 1)
        val resolution = InternationalNumberResolver().resolve("+590 690 123 456")
        val event = IntelligenceEvent(
            eventId = "telecom-1",
            identityKey = identity,
            evidence = TelecomIntelligenceEvidenceAdapter.from(
                resolution,
                sourceIds = listOf("ITU", "ARCEP")
            ),
            observedAtMs = 1_000L,
            expiresAtMs = 10_000L,
            source = EvidenceSource.TELECOM_REGULATORY,
            provenance = EvidenceProvenance(
                producer = EvidenceProvenance.Producer.TELECOM_INTELLIGENCE,
                producerVersion = 1,
                trust = EvidenceProvenance.TrustClass.UNVERIFIED_REMOTE
            ),
            privacyClass = PrivacyClass.BOUNDED_METADATA
        )
        val store = IntelligenceSnapshotStore(directory, maxEntries = 10, maxAgeMs = 60_000L)

        store.append(event, nowMs = 2_000L)

        assertEquals(event, store.read(identity, nowMs = 2_000L).single())
    }
}
