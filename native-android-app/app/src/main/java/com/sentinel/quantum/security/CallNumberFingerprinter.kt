package com.sentinel.quantum.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import java.util.concurrent.ConcurrentHashMap

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

    /**
     * Screening-safe variant: never opens AndroidKeyStore and never generates a key.
     * If the process-local key cache is not ready yet, exact-number matching fails open.
     */
    fun cachedCandidates(normalizedNumber: String): Set<String> {
        val values = linkedSetOf<String>()
        cachedKey(ACTIVE_VERSION)?.let { key ->
            computeHmacFingerprint(key, normalizedNumber)?.let { values += "$ACTIVE_VERSION:$it" }
        }
        cachedKey(LEGACY_VERSION)?.let { key ->
            computeHmacFingerprint(key, normalizedNumber)?.let {
                values += "$LEGACY_VERSION:$it"
                values += it
            }
        }
        return values
    }

    /** Loads existing Keystore keys outside the CallScreeningService critical callback. */
    fun prepareExistingKeys() {
        getKey(ACTIVE_VERSION, createIfMissing = false)
        getKey(LEGACY_VERSION, createIfMissing = false)
    }

    private fun fingerprint(
        normalizedNumber: String,
        version: String,
        createIfMissing: Boolean = true
    ): String? = runCatching {
        val key = getKey(version, createIfMissing) ?: return@runCatching null
        computeHmacFingerprint(key, normalizedNumber)
    }.getOrNull()

    @Synchronized
    private fun getKey(version: String, createIfMissing: Boolean): SecretKey? {
        val alias = "$KEY_ALIAS_PREFIX$version"
        cachedKey(version)?.let { return it }
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let {
            KEY_CACHE[version] = it
            return it
        }
        if (!createIfMissing) return null

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()
        )
        return generator.generateKey().also { KEY_CACHE[version] = it }
    }

    private fun cachedKey(version: String): SecretKey? = KEY_CACHE[version]

    private companion object {
        val KEY_CACHE = ConcurrentHashMap<String, SecretKey>()
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS_PREFIX = "sentinel_call_rule_hmac_"
        const val LEGACY_VERSION = "v1"
        const val ACTIVE_VERSION = "v2"
    }
}

/**
 * Pure HMAC-SHA256 hex-encoding, kept free of any Android Keystore dependency so it can be
 * unit-tested on the plain JVM with a standard [javax.crypto.spec.SecretKeySpec]. Only the key
 * *retrieval* in [CallNumberFingerprinter] depends on the Android Keystore; this formatting
 * logic does not.
 */
internal const val HMAC_ALGORITHM = "HmacSHA256"

internal fun computeHmacFingerprint(key: SecretKey, normalizedNumber: String): String? = runCatching {
    val mac = Mac.getInstance(HMAC_ALGORITHM)
    mac.init(key)
    mac.doFinal(normalizedNumber.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}.getOrNull()
