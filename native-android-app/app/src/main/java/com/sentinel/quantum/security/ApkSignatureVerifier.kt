package com.sentinel.quantum.security

import com.android.apksig.ApkVerifier
import java.io.File

/**
 * Cryptographic APK verification against the Android signing schemes, not just certificate parsing.
 * Never installs or executes the candidate. Fail closed on unsupported or malformed inputs.
 */
internal object ApkSignatureVerifier {
    fun verify(file: File): Boolean? = try {
        ApkVerifier.Builder(file)
            .setMinCheckedPlatformVersion(24)
            .build()
            .verify()
            .isVerified
    } catch (_: Exception) {
        // A parse failure or unsupported crypto algorithm cannot be represented as "verified".
        null
    } catch (_: LinkageError) {
        null
    }
}
