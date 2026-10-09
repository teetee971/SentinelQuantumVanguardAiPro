package com.sentinel.quantum.talkiewalkie

data class FloorLease(
    val leaseId: String,
    val holderSessionId: String,
    val expiresAtMs: Long
) {
    fun isValidFor(sessionId: String, nowMs: Long): Boolean =
        leaseId.isNotBlank() && holderSessionId == sessionId && nowMs < expiresAtMs
}
