package com.sentinel.quantum.security

import com.android.apksig.ApkVerifier
import java.io.File

object ApkSignatureVerifier {
    fun verify(file: File): Boolean {
        require(file.isFile && file.length() > 0) {
            "APK absent ou vide"
        }

        return ApkVerifier.Builder(file)
            .build()
            .verify()
            .isVerified
    }
}
