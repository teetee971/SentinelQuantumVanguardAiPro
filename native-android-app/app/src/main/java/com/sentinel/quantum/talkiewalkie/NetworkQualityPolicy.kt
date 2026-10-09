package com.sentinel.quantum.talkiewalkie

data class NetworkMetrics(
    val latencyMs: Long,
    val jitterMs: Long,
    val packetLossPercent: Double,
    val reconnectCount: Int
)

enum class NetworkQuality {
    EXCELLENT,
    GOOD,
    DEGRADED,
    UNUSABLE
}

class NetworkQualityPolicy(
    private val improvementSamplesRequired: Int = 2
) {
    init {
        require(improvementSamplesRequired >= 1) {
            "improvementSamplesRequired must be at least 1"
        }
    }

    private var currentQuality: NetworkQuality? = null
    private var pendingImprovement: NetworkQuality? = null
    private var pendingImprovementSamples: Int = 0

    fun classify(metrics: NetworkMetrics): NetworkQuality {
        if (
            metrics.latencyMs < 0 ||
            metrics.jitterMs < 0 ||
            !metrics.packetLossPercent.isFinite() ||
            metrics.packetLossPercent < 0.0 ||
            metrics.reconnectCount < 0
        ) {
            return NetworkQuality.UNUSABLE
        }

        return when {
            metrics.latencyMs <= 100 &&
                metrics.jitterMs <= 20 &&
                metrics.packetLossPercent <= 1.0 &&
                metrics.reconnectCount == 0 -> NetworkQuality.EXCELLENT

            metrics.latencyMs <= 200 &&
                metrics.jitterMs <= 40 &&
                metrics.packetLossPercent <= 3.0 &&
                metrics.reconnectCount == 0 -> NetworkQuality.GOOD

            metrics.latencyMs <= 500 &&
                metrics.jitterMs <= 100 &&
                metrics.packetLossPercent <= 10.0 &&
                metrics.reconnectCount <= 2 -> NetworkQuality.DEGRADED

            else -> NetworkQuality.UNUSABLE
        }
    }

    @Synchronized
    fun update(metrics: NetworkMetrics): NetworkQuality {
        val measured = classify(metrics)
        val current = currentQuality

        if (current == null) {
            currentQuality = measured
            resetPendingImprovement()
            return measured
        }

        val measuredRank = measured.ordinal
        val currentRank = current.ordinal

        if (measuredRank >= currentRank) {
            if (measuredRank > currentRank) {
                currentQuality = measured
            }
            resetPendingImprovement()
            return currentQuality ?: measured
        }

        if (pendingImprovement == measured) {
            pendingImprovementSamples += 1
        } else {
            pendingImprovement = measured
            pendingImprovementSamples = 1
        }

        if (pendingImprovementSamples >= improvementSamplesRequired) {
            currentQuality = measured
            resetPendingImprovement()
        }

        return currentQuality ?: measured
    }

    private fun resetPendingImprovement() {
        pendingImprovement = null
        pendingImprovementSamples = 0
    }
}
