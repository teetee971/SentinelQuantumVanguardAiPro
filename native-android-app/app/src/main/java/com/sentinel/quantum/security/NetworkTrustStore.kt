package com.sentinel.quantum.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

/**
 * Liste blanche / liste noire locale de réseaux et d'appareils environnants.
 *
 * Les identifiants (BSSID, adresses MAC) ne sont jamais persistés en clair : seule une
 * empreinte HMAC-SHA256 liée à une clé non exportable du Keystore Android est stockée,
 * comme pour les règles d'appel ([CallNumberFingerprinter]).
 */
class NetworkTrustStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isAllowed(identifier: String): Boolean = contains(KEY_ALLOWLIST, identifier)

    fun isBlocked(identifier: String): Boolean = contains(KEY_BLOCKLIST, identifier)

    fun allow(identifier: String) {
        move(identifier, addTo = KEY_ALLOWLIST, removeFrom = KEY_BLOCKLIST)
    }

    fun block(identifier: String) {
        move(identifier, addTo = KEY_BLOCKLIST, removeFrom = KEY_ALLOWLIST)
    }

    fun clear(identifier: String) {
        val fingerprint = fingerprint(identifier) ?: return
        prefs.edit()
            .putStringSet(KEY_ALLOWLIST, read(KEY_ALLOWLIST) - fingerprint)
            .putStringSet(KEY_BLOCKLIST, read(KEY_BLOCKLIST) - fingerprint)
            .apply()
    }

    private fun move(identifier: String, addTo: String, removeFrom: String) {
        val fingerprint = fingerprint(identifier) ?: return
        prefs.edit()
            .putStringSet(addTo, read(addTo) + fingerprint)
            .putStringSet(removeFrom, read(removeFrom) - fingerprint)
            .apply()
    }

    private fun contains(key: String, identifier: String): Boolean {
        val fingerprint = fingerprint(identifier) ?: return false
        return read(key).contains(fingerprint)
    }

    private fun read(key: String): Set<String> =
        prefs.getStringSet(key, emptySet())?.toSet().orEmpty()

    private fun fingerprint(identifier: String): String? {
        val normalized = identifier.trim().lowercase()
        if (normalized.isEmpty()) return null
        return runCatching {
            val mac = Mac.getInstance(HMAC_ALGORITHM)
            mac.init(getKey())
            mac.doFinal(normalized.toByteArray(Charsets.UTF_8))
                .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        }.getOrNull()
    }

    @Synchronized
    private fun getKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFS_NAME = "sentinel_network_trust"
        const val KEY_ALLOWLIST = "allowlist_fingerprints"
        const val KEY_BLOCKLIST = "blocklist_fingerprints"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val HMAC_ALGORITHM = "HmacSHA256"
        const val KEY_ALIAS = "sentinel_network_trust_hmac_v1"
    }
}
