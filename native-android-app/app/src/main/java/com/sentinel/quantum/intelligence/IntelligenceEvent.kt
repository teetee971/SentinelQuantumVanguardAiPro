package com.sentinel.quantum.intelligence

data class IntelligenceEvent(
    val eventId: String,
    val identityKey: SentinelIdentityKey?,
    val evidence: IntelligenceEvidence,
    val observedAtMs: Long,
    val expiresAtMs: Long?,
    val source: EvidenceSource,
    val provenance: EvidenceProvenance,
    val privacyClass: PrivacyClass
) {
    init {
        require(eventId.isNotBlank())
        require(observedAtMs >= 0L)
        require(expiresAtMs == null || expiresAtMs >= observedAtMs)
        if (expiresAtMs == null) {
            require(privacyClass == PrivacyClass.DURABLE_LOCAL_IDENTITY)
        }
    }
}
