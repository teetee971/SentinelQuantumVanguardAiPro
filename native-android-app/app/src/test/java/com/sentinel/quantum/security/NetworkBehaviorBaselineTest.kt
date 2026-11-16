package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkBehaviorBaselineTest {

    private fun sample(
        destination: String = "api.example.test",
        port: Int? = 443,
        protocol: NetworkBehaviorProtocol = NetworkBehaviorProtocol.TCP,
        bytes: Long = 10_000L,
        hour: Int = 12
    ) = NetworkBehaviorSample(
        subjectFingerprint = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        destination = destination,
        remotePort = port,
        protocol = protocol,
        totalBytes = bytes,
        observedHourUtc = hour
    )

    @Test
    fun insufficientHistoryNeverClaimsAnomaly() {
        val result = NetworkBehaviorBaseline.assess(
            history = List(4) { sample() },
            current = sample(destination = "new.example.test")
        )

        assertFalse(result.baselineReady)
        assertTrue(result.anomalies.isEmpty())
        assertEquals(0, result.riskScore)
    }

    @Test
    fun knownBehaviorProducesNoDeterministicAnomaly() {
        val result = NetworkBehaviorBaseline.assess(
            history = List(6) { sample(bytes = 20_000L + it) },
            current = sample(bytes = 21_000L)
        )

        assertTrue(result.baselineReady)
        assertTrue(result.anomalies.isEmpty())
        assertEquals(0, result.riskScore)
    }

    @Test
    fun newDestinationAndServiceAreSeparated() {
        val result = NetworkBehaviorBaseline.assess(
            history = List(6) { sample() },
            current = sample(destination = "unknown.example.test", port = 8443)
        )

        assertTrue(NetworkBehaviorAnomaly.NEW_DESTINATION in result.anomalies)
        assertTrue(NetworkBehaviorAnomaly.NEW_SERVICE in result.anomalies)
        assertEquals(45, result.riskScore)
    }

    @Test
    fun newPortOnKnownDestinationIsNewServiceOnly() {
        val result = NetworkBehaviorBaseline.assess(
            history = List(6) { sample() },
            current = sample(port = 8443)
        )

        assertFalse(NetworkBehaviorAnomaly.NEW_DESTINATION in result.anomalies)
        assertTrue(NetworkBehaviorAnomaly.NEW_SERVICE in result.anomalies)
        assertEquals(20, result.riskScore)
    }

    @Test
    fun unusualHourRequiresBroaderBaseline() {
        val history = (0 until 12).map { index ->
            sample(hour = if (index % 2 == 0) 8 else 9)
        }

        val result = NetworkBehaviorBaseline.assess(
            history = history,
            current = sample(hour = 3)
        )

        assertTrue(NetworkBehaviorAnomaly.UNUSUAL_HOUR in result.anomalies)
    }

    @Test
    fun volumeSpikeUsesMedianAndMinimumDelta() {
        val history = List(8) { sample(bytes = 100_000L) }

        val low = NetworkBehaviorBaseline.assess(
            history = history,
            current = sample(bytes = 350_000L)
        )
        val high = NetworkBehaviorBaseline.assess(
            history = history,
            current = sample(bytes = 400_000L)
        )

        assertFalse(NetworkBehaviorAnomaly.VOLUME_SPIKE in low.anomalies)
        assertTrue(NetworkBehaviorAnomaly.VOLUME_SPIKE in high.anomalies)
    }

    @Test
    fun historyFromOtherFingerprintIsNotMixedIntoBaseline() {
        val other = "b".repeat(64)
        val history = List(10) {
            sample().copy(subjectFingerprint = other)
        }

        val result = NetworkBehaviorBaseline.assess(
            history = history,
            current = sample()
        )

        assertFalse(result.baselineReady)
        assertEquals(0, result.baselineSampleCount)
    }

    @Test
    fun invalidCurrentSampleFailsClosed() {
        val result = NetworkBehaviorBaseline.assess(
            history = List(8) { sample() },
            current = sample().copy(subjectFingerprint = "AA:BB:CC:DD:EE:FF")
        )

        assertFalse(result.baselineReady)
        assertEquals(0, result.riskScore)
        assertTrue(result.anomalies.isEmpty())
    }
}
