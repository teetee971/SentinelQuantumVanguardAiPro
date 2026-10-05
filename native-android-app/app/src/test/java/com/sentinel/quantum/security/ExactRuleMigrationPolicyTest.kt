package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExactRuleMigrationPolicyTest {
    @Test fun legacyMetadataDecodesAsQuarantinedSemantics() {
        val decoded = CallBlockMetadataCodec.decode(
            "v2:${"a".repeat(64)}|1000||MANUAL|ancien blocage"
        )!!

        assertEquals(CallBlockMetadata.Canonicalization.LEGACY_UNSPECIFIED, decoded.canonicalization)
        assertFalse(decoded.safeForExactMatching)
        assertEquals("ancien blocage", decoded.reason)
    }

    @Test fun regionAwareMetadataRoundTripsWithoutLosingReason() {
        val entry = CallBlockMetadata.Entry(
            fingerprint = "v2:${"b".repeat(64)}",
            reason = "fraude | confirmée",
            createdAtEpochMs = 2000L,
            expiresAtEpochMs = 5000L,
            origin = CallBlockMetadata.Origin.MANUAL,
            canonicalization = CallBlockMetadata.Canonicalization.REGION_AWARE_E164_V1
        )

        val decoded = CallBlockMetadataCodec.decode(CallBlockMetadataCodec.encode(entry))!!

        assertTrue(decoded.safeForExactMatching)
        assertEquals(entry.fingerprint, decoded.fingerprint)
        assertEquals("fraude   confirmée", decoded.reason)
        assertEquals(5000L, decoded.expiresAtEpochMs)
    }

    @Test fun unknownCanonicalizationFailsClosed() {
        assertNull(
            CallBlockMetadataCodec.decode(
                "v2:${"c".repeat(64)}|1000||MANUAL|FUTURE_UNKNOWN|reason"
            )
        )
    }

    @Test fun partitionRunsOnlyCurrentSafeRulesAndQuarantinesAmbiguousHistory() {
        val safe = "v2:${"d".repeat(64)}"
        val legacy = "v2:${"e".repeat(64)}"
        val missingMetadata = "v1:${"f".repeat(64)}"
        val expiredSafe = "v2:${"1".repeat(64)}"
        val expiredLegacy = "v1:${"2".repeat(64)}"
        val metadata = mapOf(
            safe to entry(safe, CallBlockMetadata.Canonicalization.REGION_AWARE_E164_V1, expiresAt = 5000L),
            legacy to entry(legacy, CallBlockMetadata.Canonicalization.LEGACY_UNSPECIFIED, expiresAt = null),
            expiredSafe to entry(expiredSafe, CallBlockMetadata.Canonicalization.REGION_AWARE_E164_V1, expiresAt = 900L),
            expiredLegacy to entry(expiredLegacy, CallBlockMetadata.Canonicalization.LEGACY_UNSPECIFIED, expiresAt = 900L)
        )
        val hashes = setOf(safe, legacy, missingMetadata, expiredSafe, expiredLegacy)

        val partition = ExactRuleMigrationPolicy.partition(
            hashes = hashes,
            metadataByHash = metadata,
            nowEpochMs = 1000L
        )

        assertEquals(setOf(safe), partition.activeSafe)
        assertEquals(setOf(legacy, missingMetadata), partition.quarantinedLegacy)
        // Ordinary rule clearing may remove current-schema entries, including expired cleanup
        // candidates, but can never consume ambiguous legacy/missing-metadata entries.
        assertEquals(setOf(safe, expiredSafe), ExactRuleMigrationPolicy.currentSchemaHashes(hashes, metadata))
        assertEquals(
            setOf(legacy, missingMetadata, expiredLegacy),
            ExactRuleMigrationPolicy.legacyHashes(hashes, metadata)
        )
        assertEquals(
            setOf(expiredSafe, expiredLegacy),
            ExactRuleMigrationPolicy.expiredKnownHashes(metadata, nowEpochMs = 1000L)
        )
    }

    private fun entry(
        fingerprint: String,
        canonicalization: CallBlockMetadata.Canonicalization,
        expiresAt: Long?
    ) = CallBlockMetadata.Entry(
        fingerprint = fingerprint,
        reason = "test",
        createdAtEpochMs = 100L,
        expiresAtEpochMs = expiresAt,
        origin = CallBlockMetadata.Origin.MANUAL,
        canonicalization = canonicalization
    )
}
