package com.sentinel.quantum.security

/**
 * Restart/recovery policy for persisted wearable metadata.
 *
 * Persisted metadata may remember an expected identity binding, but it can never resurrect a
 * pending handshake, an authenticated channel, an ACTIVE session, replay counters or a shared
 * session secret. A fresh authenticated handshake is required after process death/reboot.
 */
data class PersistedWearableBinding(
    val stableId: String,
    val source: WearableIdentitySource,
    val keyFingerprintSha256: String
) {
    init {
        require(stableId.isNotBlank())
        require(keyFingerprintSha256.matches(Regex("^[0-9a-f]{64}$")))
    }
}

data class RecoveredWearableState(
    val expectedIdentity: WearableIdentityProof,
    val channelProof: WearableChannelProof
)

object WearableRestartPolicy {
    fun recover(binding: PersistedWearableBinding): RecoveredWearableState =
        RecoveredWearableState(
            expectedIdentity = WearableIdentityProof(
                stableId = binding.stableId,
                source = binding.source,
                keyFingerprintSha256 = binding.keyFingerprintSha256,
                verifiedAtMs = 0L
            ),
            channelProof = WearableChannelProof(
                state = WearableChannelState.NOT_NEGOTIATED,
                lastProofAtMs = null,
                ttlMs = 1L
            )
        )
}
