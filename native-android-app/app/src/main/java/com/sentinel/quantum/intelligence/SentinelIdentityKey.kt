package com.sentinel.quantum.intelligence

data class SentinelIdentityKey(val namespace: Namespace, val value: String, val version: Int) {
    init { require(value.isNotBlank()); require(version > 0) }
    enum class Namespace { PHONE_LOCAL_FINGERPRINT, ANDROID_CONTACT, SENTINEL_CONTACT, UNKNOWN }
    companion object {
        fun phoneFingerprint(fingerprint: String, version: Int) =
            SentinelIdentityKey(Namespace.PHONE_LOCAL_FINGERPRINT, fingerprint, version)
    }
}

data class IdentityAlias(
    val key: SentinelIdentityKey,
    val relationship: Relationship,
    val source: EvidenceSource
) {
    enum class Relationship { SAME_ENTITY }
}
