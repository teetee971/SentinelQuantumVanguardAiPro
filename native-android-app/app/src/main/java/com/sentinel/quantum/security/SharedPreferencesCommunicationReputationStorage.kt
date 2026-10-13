package com.sentinel.quantum.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * App-private Android storage adapter for bounded communication reputation.
 *
 * The cache supplies an opaque SHA-256 identifier, but phone numbers are low-entropy identifiers
 * and a bare digest is still enumerable. Before touching SharedPreferences this adapter derives the
 * actual persisted key with an install-local 256-bit secret and HMAC-SHA-256. The secret is stored
 * separately from reputation entries and is never exposed through this API.
 *
 * Values contain only bounded reputation metadata. Synchronous commit is intentional so storage
 * failure is observable by the cache, which converts it into a bounded false/null result.
 */
class SharedPreferencesCommunicationReputationStorage(
    context: Context
) : CommunicationReputationCache.Storage {
    private val appContext = context.applicationContext
    private val preferences: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val keySecret: ByteArray by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        loadOrCreateKeySecret(appContext)
    }

    override fun get(key: String): String? =
        preferences.getString(storageKey(key), null)

    override fun put(key: String, value: String) {
        check(preferences.edit().putString(storageKey(key), value).commit()) {
            "Unable to persist communication reputation"
        }
    }

    override fun remove(key: String) {
        check(preferences.edit().remove(storageKey(key)).commit()) {
            "Unable to remove communication reputation"
        }
    }

    private fun storageKey(cacheKey: String): String {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(keySecret, HMAC_ALGORITHM))
        val digest = mac.doFinal(cacheKey.toByteArray(StandardCharsets.UTF_8))
        return STORAGE_KEY_PREFIX + digest.joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }

    companion object {
        const val PREFS_NAME = "sentinel_communication_reputation"
        const val KEY_PREFS_NAME = "sentinel_communication_reputation_key"

        private const val SECRET_PREF_KEY = "hmac_secret_v1"
        private const val STORAGE_KEY_PREFIX = "h1:"
        private const val HMAC_ALGORITHM = "HmacSHA256"
        private const val SECRET_BYTES = 32
        private val SECRET_LOCK = Any()

        private fun loadOrCreateKeySecret(context: Context): ByteArray {
            val keyPreferences = context.getSharedPreferences(KEY_PREFS_NAME, Context.MODE_PRIVATE)
            synchronized(SECRET_LOCK) {
                decodeSecret(keyPreferences.getString(SECRET_PREF_KEY, null))?.let { return it }

                val generated = ByteArray(SECRET_BYTES).also(SecureRandom()::nextBytes)
                val encoded = Base64.encodeToString(generated, Base64.NO_WRAP)
                check(keyPreferences.edit().putString(SECRET_PREF_KEY, encoded).commit()) {
                    "Unable to persist communication reputation key"
                }
                return generated
            }
        }

        private fun decodeSecret(encoded: String?): ByteArray? {
            if (encoded.isNullOrBlank()) return null
            return runCatching { Base64.decode(encoded, Base64.NO_WRAP) }
                .getOrNull()
                ?.takeIf { it.size == SECRET_BYTES }
        }
    }
}
