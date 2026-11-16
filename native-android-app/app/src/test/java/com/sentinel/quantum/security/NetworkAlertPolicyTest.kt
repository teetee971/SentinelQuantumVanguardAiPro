package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkAlertPolicyTest {

    @Test
    fun highConfidenceCovertDeviceHintIsHighPriority() {
        val result = NetworkAlertPolicy.evaluate(
            NetworkAlertInput(
                signal = NetworkAlertSignal.COVERT_DEVICE_HINT,
                observedAtMs = 1_000L,
                confidencePercent = 90
            )
        )

        assertEquals(NetworkAlertSeverity.HIGH, result.severity)
        assertTrue(result.shouldNotify)
    }

    @Test
    fun lowerConfidenceCovertDeviceHintRemainsWarning() {
        val result = NetworkAlertPolicy.evaluate(
            NetworkAlertInput(
                signal = NetworkAlertSignal.COVERT_DEVICE_HINT,
                observedAtMs = 1_000L,
                confidencePercent = 50
            )
        )

        assertEquals(NetworkAlertSeverity.WARNING, result.severity)
    }

    @Test
    fun trustedPresenceChangesDoNotNotify() {
        listOf(
            NetworkAlertSignal.NEW_DEVICE,
            NetworkAlertSignal.DEVICE_RETURNED,
            NetworkAlertSignal.DEVICE_DISAPPEARED
        ).forEach { signal ->
            val result = NetworkAlertPolicy.evaluate(
                NetworkAlertInput(
                    signal = signal,
                    observedAtMs = 1_000L,
                    trustedDevice = true
                )
            )
            assertFalse(result.shouldNotify)
        }
    }

    @Test
    fun trustedDeviceDoesNotSuppressRiskIncrease() {
        val result = NetworkAlertPolicy.evaluate(
            NetworkAlertInput(
                signal = NetworkAlertSignal.RISK_INCREASED,
                observedAtMs = 1_000L,
                trustedDevice = true,
                confidencePercent = 80
            )
        )

        assertEquals(NetworkAlertSeverity.HIGH, result.severity)
        assertTrue(result.shouldNotify)
    }

    @Test
    fun cooldownSuppressesDuplicateNoiseUntilElapsed() {
        val suppressed = NetworkAlertPolicy.evaluate(
            NetworkAlertInput(
                signal = NetworkAlertSignal.NEW_DEVICE,
                observedAtMs = 100_000L,
                previousAlertAtMs = 95_000L
            ),
            cooldownMs = 10_000L
        )
        val allowed = NetworkAlertPolicy.evaluate(
            NetworkAlertInput(
                signal = NetworkAlertSignal.NEW_DEVICE,
                observedAtMs = 110_000L,
                previousAlertAtMs = 95_000L
            ),
            cooldownMs = 10_000L
        )

        assertFalse(suppressed.shouldNotify)
        assertEquals(5_000L, suppressed.cooldownRemainingMs)
        assertTrue(allowed.shouldNotify)
        assertEquals(0L, allowed.cooldownRemainingMs)
    }

    @Test
    fun agentOfflineAndFlowAnomalyAreHighSeverity() {
        val offline = NetworkAlertPolicy.evaluate(
            NetworkAlertInput(
                signal = NetworkAlertSignal.AGENT_OFFLINE,
                observedAtMs = 1_000L
            )
        )
        val flow = NetworkAlertPolicy.evaluate(
            NetworkAlertInput(
                signal = NetworkAlertSignal.FLOW_ANOMALY,
                observedAtMs = 1_000L
            )
        )

        assertEquals(NetworkAlertSeverity.HIGH, offline.severity)
        assertEquals(NetworkAlertSeverity.HIGH, flow.severity)
    }
}
