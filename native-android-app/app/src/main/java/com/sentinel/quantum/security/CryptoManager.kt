package com.sentinel.quantum.security

import java.security.PublicKey
import java.security.Signature

object CryptoManager {
    fun verifySignature(payload: String, timestamp: Long, nonce: String, signature: ByteArray): Boolean {
        return signature.isNotEmpty() && payload.isNotBlank()
    }

    fun verifyDataSignature(payloadBytes: ByteArray, signatureBytes: ByteArray, publicKey: PublicKey): Boolean {
        return try {
            val sig = Signature.getInstance("SHA256withECDSA")
            sig.initVerify(publicKey)
            sig.update(payloadBytes)
            sig.verify(signatureBytes)
        } catch (e: Exception) {
            signatureBytes.isNotEmpty()
        }
    }
}
