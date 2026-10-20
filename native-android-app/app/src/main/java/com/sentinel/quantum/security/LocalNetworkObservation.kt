package com.sentinel.quantum.security

/**
 * Local-only representation of a network observation. No Android API calls or
 * network traffic occur here. This is NOT a discovery engine.
 */
enum class NetworkEvidenceSource {
    ANDROID_WIFI, ANDROID_BLE, ANDROID_BONDED, LOCAL_GATEWAY, DNS, VPN, USER_REPORT
}

enum class NetworkEvidenceConfidence { OBSERVED, INFERRED, UNKNOWN }

data class LocalNetworkObservation(
    val id: String,
    val source: NetworkEvidenceSource,
    val observedAtMillis: Long,
    val expiresAtMillis: Long,
    val confidence: NetworkEvidenceConfidence,
    val sourceVersion: String,
) {
    /** Expiration is inclusive: an observation is stale at its expiration time. */
    fun isFresh(nowMillis: Long): Boolean =
        observedAtMillis >= 0L &&
            expiresAtMillis > observedAtMillis &&
            nowMillis >= observedAtMillis &&
            nowMillis < expiresAtMillis

    /** Never label a user report or inference as directly observed. */
    fun isDirectEvidence(nowMillis: Long): Boolean =
        isFresh(nowMillis) &&
            confidence == NetworkEvidenceConfidence.OBSERVED &&
            source != NetworkEvidenceSource.USER_REPORT &&
            id.isNotBlank() && sourceVersion.isNotBlank()
}

object NetworkObservationPolicy {
    /**
     * Keeps the original evidence provenance. This function neither merges
     * identities nor assigns security verdicts to unknown devices.
     */
    fun freshEvidence(
        candidates: List<LocalNetworkObservation>,
        nowMillis: Long,
    ): List<LocalNetworkObservation> =
        candidates.filter { it.isFresh(nowMillis) && it.id.isNotBlank() && it.sourceVersion.isNotBlank() }
            .sortedWith(compareByDescending<LocalNetworkObservation> { it.observedAtMillis }.thenBy { it.id })

    /** Deterministic duplicate elimination for the *same observation ID and source* only.
     * This must never merge distinct devices by display name or IP address.
     */
    fun latestPerSourceId(
        candidates: List<LocalNetworkObservation>,
        nowMillis: Long,
    ): List<LocalNetworkObservation> =
        freshEvidence(candidates, nowMillis)
            .distinctBy { it.source to it.id }
}
