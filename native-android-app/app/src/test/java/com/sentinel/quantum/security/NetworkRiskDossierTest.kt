package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkRiskDossierTest {

    @Test
    fun noEvidenceNeverClaimsSafety() {
        val dossier = NetworkRiskDossierBuilder.build("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", emptyList())!!

        assertEquals(0, dossier.riskScore)
        assertEquals(NetworkRiskConfidence.UNKNOWN, dossier.confidence)
        assertFalse(dossier.corroborated)
        assertTrue(dossier.explanation.contains("ne prouve pas"))
    }

    @Test
    fun oneSourceRemainsIndicative() {
        val dossier = NetworkRiskDossierBuilder.build(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            listOf(
                NetworkRiskEvidence(
                    type = NetworkRiskEvidenceType.COVERT_DEVICE_HINT,
                    source = NetworkRiskEvidenceSource.ANDROID_BLE,
                    confidencePercent = 80,
                    observedAtMs = 1_000L,
                    summary = "Nom BLE compatible avec un traceur"
                )
            )
        )!!

        assertEquals(NetworkRiskConfidence.INDICATIVE, dossier.confidence)
        assertFalse(dossier.corroborated)
        assertEquals(24, dossier.riskScore)
    }

    @Test
    fun twoIndependentSourcesBecomeCorroborated() {
        val dossier = NetworkRiskDossierBuilder.build(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            listOf(
                NetworkRiskEvidence(
                    NetworkRiskEvidenceType.COVERT_DEVICE_HINT,
                    NetworkRiskEvidenceSource.ANDROID_BLE,
                    80,
                    1_000L,
                    "Indice BLE"
                ),
                NetworkRiskEvidence(
                    NetworkRiskEvidenceType.FLOW_ANOMALY,
                    NetworkRiskEvidenceSource.FLOW_ANALYZER,
                    70,
                    2_000L,
                    "Destination inhabituelle"
                )
            )
        )!!

        assertEquals(NetworkRiskConfidence.CORROBORATED, dossier.confidence)
        assertTrue(dossier.corroborated)
        assertEquals(2, dossier.independentSourceCount)
        assertEquals(55, dossier.riskScore)
    }

    @Test
    fun threeSourcesCreateStrongCorroborationAndScoreIsBounded() {
        val dossier = NetworkRiskDossierBuilder.build(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            listOf(
                NetworkRiskEvidence(
                    NetworkRiskEvidenceType.COVERT_DEVICE_HINT,
                    NetworkRiskEvidenceSource.ANDROID_BLE,
                    100,
                    1_000L,
                    "Indice BLE"
                ),
                NetworkRiskEvidence(
                    NetworkRiskEvidenceType.FLOW_ANOMALY,
                    NetworkRiskEvidenceSource.FLOW_ANALYZER,
                    100,
                    2_000L,
                    "Flux inhabituel"
                ),
                NetworkRiskEvidence(
                    NetworkRiskEvidenceType.BEHAVIOR_ANOMALY,
                    NetworkRiskEvidenceSource.BEHAVIOR_BASELINE,
                    100,
                    3_000L,
                    "Comportement nouveau"
                ),
                NetworkRiskEvidence(
                    NetworkRiskEvidenceType.SERVICE_EXPOSURE,
                    NetworkRiskEvidenceSource.LOCAL_AGENT,
                    100,
                    4_000L,
                    "Service exposé"
                )
            )
        )!!

        assertEquals(NetworkRiskConfidence.STRONG_CORROBORATION, dossier.confidence)
        assertEquals(100, dossier.riskScore)
        assertTrue(dossier.corroborated)
    }

    @Test
    fun duplicateEvidenceDoesNotInflateDossier() {
        val evidence = NetworkRiskEvidence(
            NetworkRiskEvidenceType.IDENTITY_CHANGE,
            NetworkRiskEvidenceSource.LOCAL_AGENT,
            80,
            1_000L,
            "Identité modifiée"
        )

        val dossier = NetworkRiskDossierBuilder.build("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", listOf(evidence, evidence))!!

        assertEquals(1, dossier.evidenceCount)
        assertEquals(12, dossier.riskScore)
    }

    @Test
    fun invalidEvidenceIsDiscarded() {
        val dossier = NetworkRiskDossierBuilder.build(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            listOf(
                NetworkRiskEvidence(
                    NetworkRiskEvidenceType.FLOW_ANOMALY,
                    NetworkRiskEvidenceSource.FLOW_ANALYZER,
                    101,
                    1_000L,
                    "Confiance invalide"
                ),
                NetworkRiskEvidence(
                    NetworkRiskEvidenceType.FLOW_ANOMALY,
                    NetworkRiskEvidenceSource.FLOW_ANALYZER,
                    80,
                    -1L,
                    "Temps invalide"
                ),
                NetworkRiskEvidence(
                    NetworkRiskEvidenceType.FLOW_ANOMALY,
                    NetworkRiskEvidenceSource.FLOW_ANALYZER,
                    80,
                    1_000L,
                    "   "
                )
            )
        )!!

        assertEquals(0, dossier.evidenceCount)
        assertEquals(NetworkRiskConfidence.UNKNOWN, dossier.confidence)
    }

    @Test
    fun rawMacStyleSubjectIsRejected() {
        assertNull(
            NetworkRiskDossierBuilder.build(
                "AA:BB:CC:DD:EE:FF",
                emptyList()
            )
        )
    }

    @Test
    fun topReasonsAreRankedByContributionAndBounded() {
        val evidence = listOf(
            NetworkRiskEvidence(NetworkRiskEvidenceType.PRESENCE_CHANGE, NetworkRiskEvidenceSource.ANDROID_WIFI, 100, 1L, "Présence"),
            NetworkRiskEvidence(NetworkRiskEvidenceType.IDENTITY_CHANGE, NetworkRiskEvidenceSource.LOCAL_AGENT, 100, 2L, "Identité"),
            NetworkRiskEvidence(NetworkRiskEvidenceType.TOPOLOGY_CHANGE, NetworkRiskEvidenceSource.TOPOLOGY_ENGINE, 100, 3L, "Topologie"),
            NetworkRiskEvidence(NetworkRiskEvidenceType.COVERT_DEVICE_HINT, NetworkRiskEvidenceSource.ANDROID_BLE, 100, 4L, "Caché"),
            NetworkRiskEvidence(NetworkRiskEvidenceType.FLOW_ANOMALY, NetworkRiskEvidenceSource.FLOW_ANALYZER, 100, 5L, "Flux"),
            NetworkRiskEvidence(NetworkRiskEvidenceType.BEHAVIOR_ANOMALY, NetworkRiskEvidenceSource.BEHAVIOR_BASELINE, 100, 6L, "Comportement"),
            NetworkRiskEvidence(NetworkRiskEvidenceType.SERVICE_EXPOSURE, NetworkRiskEvidenceSource.USER_CONFIRMED, 100, 7L, "Service")
        )

        val dossier = NetworkRiskDossierBuilder.build("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", evidence)!!

        assertEquals(5, dossier.topReasons.size)
        assertEquals(30, dossier.topReasons.first().contribution)
        assertTrue(
            dossier.topReasons.all { it.type != NetworkRiskEvidenceType.PRESENCE_CHANGE }
        )
    }
}
