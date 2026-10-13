package com.sentinel.quantum.intelligence

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntelligenceSnapshotStoreTest {
    private val identity = SentinelIdentityKey.phoneFingerprint("fp-store-test", 1)

    private fun reputationEvent(
        eventId: String,
        observedAtMs: Long,
        expiresAtMs: Long,
        score: Int = 82
    ) = IntelligenceEvent(
        eventId = eventId,
        identityKey = identity,
        evidence = IntelligenceEvidence.Reputation(
            riskScore = score,
            communitySignals = 2,
            categoryCodes = listOf("fraud"),
            sourceLabel = "signed-feed",
            signedPackageVerified = true
        ),
        observedAtMs = observedAtMs,
        expiresAtMs = expiresAtMs,
        source = EvidenceSource.REPUTATION_REMOTE,
        provenance = EvidenceProvenance(
            producer = EvidenceProvenance.Producer.REPUTATION_CORE,
            producerVersion = 1,
            trust = EvidenceProvenance.TrustClass.SIGNED_REMOTE_ASSERTION,
            certification = "cert-v1",
            issuer = "sentinel-test"
        ),
        privacyClass = PrivacyClass.BOUNDED_METADATA
    )

    private fun newDirectory(): File = Files.createTempDirectory("sentinel-intelligence-store").toFile()

    @Test
    fun serialized_storage_contains_no_raw_sensitive_fixtures() {
        val directory = newDirectory()
        val store = IntelligenceSnapshotStore(directory, maxEntries = 10, maxAgeMs = 60_000L)
        val rawFixtures = listOf(
            "+33612345678",
            "Votre code OTP est 123456",
            "https://evil.example/reset",
            "sender@example.test",
            "123456"
        )

        store.append(reputationEvent("safe-1", 1_000L, 10_000L), nowMs = 2_000L)

        val serialized = directory.walkTopDown()
            .filter { it.isFile }
            .joinToString("\n") { it.readText() }
        rawFixtures.forEach { fixture -> assertFalse(serialized.contains(fixture)) }
    }

    @Test
    fun corrupted_payload_returns_empty_state_instead_of_fabricated_facts() {
        val directory = newDirectory()
        File(directory, IntelligenceSnapshotStore.FILE_NAME).writeText("not-a-valid-store")
        val store = IntelligenceSnapshotStore(directory, maxEntries = 10, maxAgeMs = 60_000L)

        assertTrue(store.read(identity, nowMs = 2_000L).isEmpty())
    }

    @Test
    fun unknown_future_schema_fails_safely() {
        val directory = newDirectory()
        File(directory, IntelligenceSnapshotStore.FILE_NAME).writeText("schema=999\n")
        val store = IntelligenceSnapshotStore(directory, maxEntries = 10, maxAgeMs = 60_000L)

        assertTrue(store.read(identity, nowMs = 2_000L).isEmpty())
    }

    @Test
    fun retention_enforces_maximum_age_and_count() {
        val directory = newDirectory()
        val store = IntelligenceSnapshotStore(directory, maxEntries = 2, maxAgeMs = 1_000L)

        store.append(reputationEvent("expired", 100L, 5_000L), nowMs = 2_000L)
        store.append(reputationEvent("kept-1", 1_500L, 5_000L), nowMs = 2_000L)
        store.append(reputationEvent("kept-2", 1_600L, 5_000L), nowMs = 2_000L)
        store.append(reputationEvent("kept-3", 1_700L, 5_000L), nowMs = 2_000L)

        assertEquals(listOf("kept-2", "kept-3"), store.read(identity, nowMs = 2_000L).map { it.eventId })
    }

    @Test
    fun duplicate_event_id_is_an_idempotent_no_op() {
        val directory = newDirectory()
        val store = IntelligenceSnapshotStore(directory, maxEntries = 10, maxAgeMs = 60_000L)
        val original = reputationEvent("same-id", 1_000L, 10_000L, score = 82)
        val conflictingDuplicate = reputationEvent("same-id", 1_500L, 10_000L, score = 7)

        store.append(original, nowMs = 2_000L)
        store.append(conflictingDuplicate, nowMs = 2_000L)

        assertEquals(listOf(original), store.read(identity, nowMs = 2_000L))
    }

    @Test
    fun restart_rebuild_reproduces_snapshot_from_same_retained_evidence() {
        val directory = newDirectory()
        val originalStore = IntelligenceSnapshotStore(directory, maxEntries = 10, maxAgeMs = 60_000L)
        val event = reputationEvent("r1", 1_000L, 10_000L)
        originalStore.append(event, nowMs = 2_000L)
        val expected = IntelligenceAggregator().aggregate(identity, listOf(event), nowMs = 2_000L)

        val restartedStore = IntelligenceSnapshotStore(directory, maxEntries = 10, maxAgeMs = 60_000L)
        val rebuilt = restartedStore.rebuild(identity, nowMs = 2_000L)

        assertEquals(expected, rebuilt)
    }

    @Test
    fun call_screening_critical_path_does_not_reference_snapshot_store() {
        val source = File("src/main/java/com/sentinel/quantum/security/SentinelCallScreeningService.kt").readText()
        val start = source.indexOf("onScreenCall")
        assertTrue("onScreenCall() must exist", start >= 0)
        val nextMethod = source.indexOf("\n    override fun ", start + 1).let { if (it < 0) source.length else it }
        val criticalPath = source.substring(start, nextMethod)

        assertFalse(criticalPath.contains("IntelligenceSnapshotStore"))
        assertFalse(criticalPath.contains("intelligenceSnapshotStore"))
    }
}
