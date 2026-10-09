package com.sentinel.quantum.talkiewalkie

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkQualityPolicyTest {
    @Test
    fun classifiesDeterministicQualityBands() {
        val policy = NetworkQualityPolicy()

        assertEquals(
            NetworkQuality.EXCELLENT,
            policy.classify(NetworkMetrics(80, 10, 0.5, 0))
        )
        assertEquals(
            NetworkQuality.GOOD,
            policy.classify(NetworkMetrics(180, 35, 2.0, 0))
        )
        assertEquals(
            NetworkQuality.DEGRADED,
            policy.classify(NetworkMetrics(450, 90, 8.0, 1))
        )
        assertEquals(
            NetworkQuality.UNUSABLE,
            policy.classify(NetworkMetrics(900, 200, 20.0, 3))
        )
    }

    @Test
    fun degradationIsImmediateButImprovementRequiresTwoSamples() {
        val policy = NetworkQualityPolicy(improvementSamplesRequired = 2)

        assertEquals(
            NetworkQuality.DEGRADED,
            policy.update(NetworkMetrics(400, 80, 7.0, 1))
        )
        assertEquals(
            NetworkQuality.DEGRADED,
            policy.update(NetworkMetrics(160, 25, 2.0, 0))
        )
        assertEquals(
            NetworkQuality.GOOD,
            policy.update(NetworkMetrics(150, 20, 1.5, 0))
        )
        assertEquals(
            NetworkQuality.UNUSABLE,
            policy.update(NetworkMetrics(850, 160, 18.0, 4))
        )
    }
}
