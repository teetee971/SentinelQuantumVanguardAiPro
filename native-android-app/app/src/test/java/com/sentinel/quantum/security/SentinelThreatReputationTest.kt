package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class SentinelThreatReputationTest {

    @Test
    fun sha256StreamingDigestMatchesKnownVector() {
        val digest = SentinelSha256.digest(ByteArrayInputStream("abc".toByteArray()))
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            digest
        )
    }

    @Test
    fun invalidHashesAreNeverAcceptedIntoReputation() {
        val provider = SentinelThreatReputation.LocalHashSet(
            malicious = mapOf("not-a-hash" to "bad")
        )

        assertFalse(provider.isAvailable)
        assertEquals(
            SentinelThreatReputation.Verdict.UNKNOWN,
            provider.lookupSha256("not-a-hash").verdict
        )
    }

    @Test
    fun knownMaliciousHashProducesMaliciousFinding() {
        val hash = "a".repeat(64)
        val provider = SentinelThreatReputation.LocalHashSet(
            malicious = mapOf(hash to "Test.Signature")
        )

        val result = provider.lookupSha256(hash.uppercase())
        val finding = SentinelThreatReputationPolicy.findingFor(
            packageName = "example.bad",
            sha256 = hash,
            result = result,
            observedAtEpochMillis = 10L
        )

        assertTrue(provider.isAvailable)
        assertEquals(SentinelMalwareDiagnostic.Verdict.MALICIOUS, finding?.verdict)
        assertEquals("Test.Signature", finding?.observedValue)
    }

    @Test
    fun unknownHashNeverBecomesCleanFinding() {
        val provider = SentinelThreatReputation.LocalHashSet(
            malicious = mapOf("b".repeat(64) to "Known.Bad")
        )

        val result = provider.lookupSha256("c".repeat(64))
        val finding = SentinelThreatReputationPolicy.findingFor(
            packageName = "example.unknown",
            sha256 = "c".repeat(64),
            result = result,
            observedAtEpochMillis = 20L
        )

        assertEquals(SentinelThreatReputation.Verdict.UNKNOWN, result.verdict)
        assertNull(finding)
    }
}
