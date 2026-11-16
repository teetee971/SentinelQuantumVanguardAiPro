package com.sentinel.quantum.security

/**
 * Deterministic local alert policy for network-intelligence signals.
 *
 * This layer consumes already-classified signals. It performs no scan, packet capture,
 * notification delivery, persistence or network access. A later adapter can map Device
 * Registry, Local Agent, Covert Device Watch and Flow Analyzer outputs into this model.
 */
enum class NetworkAlertSignal {
    NEW_DEVICE,
    DEVICE_RETURNED,
    DEVICE_DISAPPEARED,
    IDENTITY_CHANGED,
    RISK_INCREASED,
    COVERT_DEVICE_HINT,
    AGENT_DEGRADED,
    AGENT_OFFLINE,
    FLOW_ANOMALY
}

enum class NetworkAlertSeverity {
    INFO,
    WARNING,
    HIGH
}

data class NetworkAlertInput(
    val signal: NetworkAlertSignal,
    val observedAtMs: Long,
    val previousAlertAtMs: Long? = null,
    val trustedDevice: Boolean = false,
    val confidencePercent: Int = 0
)

data class NetworkAlertDecision(
    val severity: NetworkAlertSeverity,
    val shouldNotify: Boolean,
    val reason: String,
    val cooldownRemainingMs: Long
)

object NetworkAlertPolicy {
    const val DEFAULT_COOLDOWN_MS = 15L * 60L * 1000L
    private const val HIGH_CONFIDENCE = 75

    fun evaluate(
        input: NetworkAlertInput,
        cooldownMs: Long = DEFAULT_COOLDOWN_MS
    ): NetworkAlertDecision {
        require(input.observedAtMs >= 0L) { "observedAtMs must be non-negative" }
        require(input.confidencePercent in 0..100) { "confidencePercent must be between 0 and 100" }
        require(cooldownMs >= 0L) { "cooldownMs must be non-negative" }

        val severity = severityFor(input)
        val remaining = cooldownRemaining(
            previousAlertAtMs = input.previousAlertAtMs,
            observedAtMs = input.observedAtMs,
            cooldownMs = cooldownMs
        )

        val suppressedByTrust = input.trustedDevice &&
            input.signal in setOf(
                NetworkAlertSignal.NEW_DEVICE,
                NetworkAlertSignal.DEVICE_RETURNED,
                NetworkAlertSignal.DEVICE_DISAPPEARED
            )

        val shouldNotify = !suppressedByTrust && remaining == 0L

        val reason = when {
            suppressedByTrust ->
                "Événement de présence sur un appareil explicitement approuvé par l’utilisateur."
            remaining > 0L ->
                "Alerte similaire temporairement supprimée par le délai anti-bruit."
            severity == NetworkAlertSeverity.HIGH ->
                "Signal réseau prioritaire nécessitant une vérification utilisateur."
            severity == NetworkAlertSeverity.WARNING ->
                "Changement réseau significatif à vérifier."
            else ->
                "Événement réseau informatif."
        }

        return NetworkAlertDecision(
            severity = severity,
            shouldNotify = shouldNotify,
            reason = reason,
            cooldownRemainingMs = remaining
        )
    }

    private fun severityFor(input: NetworkAlertInput): NetworkAlertSeverity = when (input.signal) {
        NetworkAlertSignal.COVERT_DEVICE_HINT ->
            if (input.confidencePercent >= HIGH_CONFIDENCE) NetworkAlertSeverity.HIGH
            else NetworkAlertSeverity.WARNING
        NetworkAlertSignal.RISK_INCREASED,
        NetworkAlertSignal.FLOW_ANOMALY,
        NetworkAlertSignal.AGENT_OFFLINE ->
            NetworkAlertSeverity.HIGH
        NetworkAlertSignal.NEW_DEVICE,
        NetworkAlertSignal.IDENTITY_CHANGED,
        NetworkAlertSignal.AGENT_DEGRADED ->
            NetworkAlertSeverity.WARNING
        NetworkAlertSignal.DEVICE_RETURNED,
        NetworkAlertSignal.DEVICE_DISAPPEARED ->
            NetworkAlertSeverity.INFO
    }

    private fun cooldownRemaining(
        previousAlertAtMs: Long?,
        observedAtMs: Long,
        cooldownMs: Long
    ): Long {
        if (previousAlertAtMs == null || cooldownMs == 0L) return 0L
        val boundedPrevious = previousAlertAtMs.coerceAtMost(observedAtMs)
        val elapsed = observedAtMs - boundedPrevious
        return (cooldownMs - elapsed).coerceAtLeast(0L)
    }
}
