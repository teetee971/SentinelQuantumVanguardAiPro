package com.sentinel.quantum.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

/**
 * Versioned, non-exportable device-bound HMAC fingerprints.
 *
 * Raw legacy v1 fingerprints remain readable because raw phone numbers are intentionally not
 * persisted and therefore cannot be re-hashed during key rotation. New rules are always written
 * with the active key version. A future explicit user migration can retire v1 after re-enrolment.
 */
class CallNumberFingerprinter {
    fun fingerprint(normalizedNumber: String): String? =
        fingerprint(normalizedNumber, ACTIVE_VERSION)?.let { "$ACTIVE_VERSION:$it" }

    fun candidates(normalizedNumber: String): Set<String> {
        val values = linkedSetOf<String>()
        fingerprint(normalizedNumber, ACTIVE_VERSION, createIfMissing = true)?.let { values += "$ACTIVE_VERSION:$it" }
        fingerprint(normalizedNumber, LEGACY_VERSION, createIfMissing = false)?.let {
            values += "$LEGACY_VERSION:$it"
            values += it // Compatibility with unversioned values written before key rotation.
        }
        return values
    }

    private fun fingerprint(
        normalizedNumber: String,
        version: String,
        createIfMissing: Boolean = true
    ): String? = runCatching {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        val key = getKey(version, createIfMissing) ?: return@runCatching null
        mac.init(key)
        mac.doFinal(normalizedNumber.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }.getOrNull()

    @Synchronized
    private fun getKey(version: String, createIfMissing: Boolean): SecretKey? {
        val alias = "$KEY_ALIAS_PREFIX$version"
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        if (!createIfMissing) return null

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val HMAC_ALGORITHM = "HmacSHA256"
        const val KEY_ALIAS_PREFIX = "sentinel_call_rule_hmac_"
        const val LEGACY_VERSION = "v1"
        const val ACTIVE_VERSION = "v2"
    }
}
