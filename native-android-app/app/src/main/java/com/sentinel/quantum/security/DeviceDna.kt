package com.sentinel.quantum.security

/**
 * Local-only identity synthesis for devices observed through Android network APIs.
 *
 * Device DNA is evidence aggregation, not identity proof. No identifier leaves the
 * device and no vendor/model claim is made without an explicit evidence source.
 */
enum class DeviceEvidenceSource {
    WIFI_SSID,
    WIFI_BSSID,
    BLE_NAME,
    BLE_ADDRESS,
    SERVICE_HINT,
    USER_TRUST
}

data class DeviceIdentityEvidence(
    val source: DeviceEvidenceSource,
    val value: String,
    val weight: Int
)

data class DeviceDnaProfile(
    val label: String,
    val confidencePercent: Int,
    val evidenceCount: Int,
    val sources: Set<DeviceEvidenceSource>,
    val trustedByUser: Boolean
)

object DeviceDnaBuilder {
    private const val MAX_LABEL_LENGTH = 96
    private const val MAX_EVIDENCE_VALUE_LENGTH = 160

    fun build(
        preferredLabel: String?,
        evidence: List<DeviceIdentityEvidence>,
        trustedByUser: Boolean
    ): DeviceDnaProfile {
        val normalizedEvidence = evidence
            .asSequence()
            .mapNotNull(::normalize)
            .distinctBy { it.source to it.value.lowercase() }
            .toList()

        val weighted = normalizedEvidence.sumOf { it.weight.coerceIn(0, 30) } +
            if (trustedByUser) 20 else 0

        return DeviceDnaProfile(
            label = preferredLabel
                ?.trim()
                ?.take(MAX_LABEL_LENGTH)
                ?.takeIf { it.isNotEmpty() }
                ?: "Appareil inconnu",
            confidencePercent = weighted.coerceIn(0, 100),
            evidenceCount = normalizedEvidence.size,
            sources = normalizedEvidence.map { it.source }.toSet(),
            trustedByUser = trustedByUser
        )
    }

    private fun normalize(item: DeviceIdentityEvidence): DeviceIdentityEvidence? {
        val value = item.value.trim().take(MAX_EVIDENCE_VALUE_LENGTH)
        if (value.isEmpty()) return null
        return item.copy(value = value, weight = item.weight.coerceIn(0, 30))
    }
}
