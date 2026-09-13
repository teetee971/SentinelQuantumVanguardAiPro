package com.sentinel.quantum.security

import kotlin.math.abs

data class AuthProof(
    val payload: String,
    val timestamp: Long,
    val nonce: String,
    val signature: ByteArray
) {
    fun isValid(currentTimestamp: Long, maxDriftMs: Long = 300_000): Boolean {
        if (abs(currentTimestamp - timestamp) > maxDriftMs) return false
        return CryptoManager.verifySignature(payload, timestamp, nonce, signature)
    }
}
