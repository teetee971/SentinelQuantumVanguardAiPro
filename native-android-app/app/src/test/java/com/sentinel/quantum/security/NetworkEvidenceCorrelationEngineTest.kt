package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkEvidenceCorrelationEngineTest {

    @Test
    fun twoIndependentSourcesCorroborateSameSubject() {
        val result = NetworkEvidenceCorrelationEngine.correlate(
            listOf(
                NetworkEvidenceRecord(
                    subjectFingerprint = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    kind = NetworkEvidenceKind.COVERT_DEVICE_HINT,
                    source = NetworkEvidenceSource.ANDROID_BLE,
                    confidencePercent = 80,
                    observedAtMs = 1_000L
                ),
                NetworkEvidenceRecord(
                    subjectFingerprint = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    kind = NetworkEvidenceKind.FLOW_ANOMALY,
                    source = NetworkEvidenceSource.FLOW_ANALYZER,
                    confidencePercent = 70,
                    observedAtMs = 2_000L
                )
            )
        )

        val subject = result.subjects.single()
        assertTrue(subject.corroborated)
        assertEquals(2, subject.independentSourceCount)
        assertEquals(2, subject.evidenceCount)
        assertTrue(subject.riskScore > 0)
    }

    @Test
    fun repeatedEvidenceFromSameSourceDoesNotBecomeIndependentCorroboration() {
        val result = NetworkEvidenceCorrelationEngine.correlate(
            listOf(
                NetworkEvidenceRecord(
                    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    NetworkEvidenceKind.RISK_SIGNAL,
                    NetworkEvidenceSource.ANDROID_WIFI,
                    80,
                    1_000L
                ),
                NetworkEvidenceRecord(
                    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    NetworkEvidenceKind.IDENTITY_CHANGE,
                    NetworkEvidenceSource.ANDROID_WIFI,
                    80,
                    2_000L
                )
            )
        )

        val subject = result.subjects.single()
        assertFalse(subject.corroborated)
        assertEquals(1, subject.independentSourceCount)
    }

    @Test
    fun duplicateRecordIsRejected() {
        val record = NetworkEvidenceRecord(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            NetworkEvidenceKind.PRESENCE,
            NetworkEvidenceSource.LOCAL_AGENT,
            50,
            1_000L
        )

        val result = NetworkEvidenceCorrelationEngine.correlate(listOf(record, record))

        assertEquals(1, result.acceptedEvidence)
        assertEquals(1, result.rejectedEvidence)
    }

    @Test
    fun rawMacLikeIdentifierIsRejected() {
        val result = NetworkEvidenceCorrelationEngine.correlate(
            listOf(
                NetworkEvidenceRecord(
                    "AA:BB:CC:DD:EE:FF",
                    NetworkEvidenceKind.PRESENCE,
                    NetworkEvidenceSource.ANDROID_WIFI,
                    50,
                    1_000L
                )
            )
        )

        assertEquals(0, result.acceptedEvidence)
        assertEquals(1, result.rejectedEvidence)
        assertTrue(result.subjects.isEmpty())
    }

    @Test
    fun separateSubjectsRemainSeparateAndSortByRisk() {
        val result = NetworkEvidenceCorrelationEngine.correlate(
            listOf(
                NetworkEvidenceRecord(
                    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    NetworkEvidenceKind.PRESENCE,
                    NetworkEvidenceSource.ANDROID_WIFI,
                    20,
                    1_000L
                ),
                NetworkEvidenceRecord(
                    "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                    NetworkEvidenceKind.COVERT_DEVICE_HINT,
                    NetworkEvidenceSource.ANDROID_BLE,
                    100,
                    2_000L
                )
            )
        )

        assertEquals(2, result.subjects.size)
        assertEquals("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", result.subjects.first().subjectFingerprint)
    }

    @Test
    fun threeSourcesReceiveHigherCorroborationBonusButScoreIsBounded() {
        val result = NetworkEvidenceCorrelationEngine.correlate(
            listOf(
                NetworkEvidenceRecord("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", NetworkEvidenceKind.COVERT_DEVICE_HINT, NetworkEvidenceSource.ANDROID_BLE, 100, 1_000L),
                NetworkEvidenceRecord("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", NetworkEvidenceKind.FLOW_ANOMALY, NetworkEvidenceSource.FLOW_ANALYZER, 100, 2_000L),
                NetworkEvidenceRecord("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", NetworkEvidenceKind.TOPOLOGY_CHANGE, NetworkEvidenceSource.TOPOLOGY_ENGINE, 100, 3_000L),
                NetworkEvidenceRecord("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", NetworkEvidenceKind.RISK_SIGNAL, NetworkEvidenceSource.LOCAL_AGENT, 100, 4_000L)
            )
        )

        val subject = result.subjects.single()
        assertTrue(subject.corroborated)
        assertEquals(100, subject.riskScore)
    }
}
