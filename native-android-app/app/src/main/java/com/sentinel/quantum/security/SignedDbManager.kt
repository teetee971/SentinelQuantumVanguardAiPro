package com.sentinel.quantum.security

import java.io.File
import java.security.PublicKey

object SignedDbManager {
    fun importSignedDb(dbFile: File, signatureFile: File, publicKey: PublicKey) {
        val payloadBytes = dbFile.readBytes()
        val sigBytes = signatureFile.readBytes()
        require(CryptoManager.verifyDataSignature(payloadBytes, sigBytes, publicKey)) {
            "Signature de base communautaire invalide ou corrompue."
        }
        // TODO: Remplacer/mettre à jour la base SQLite locale
    }
}
