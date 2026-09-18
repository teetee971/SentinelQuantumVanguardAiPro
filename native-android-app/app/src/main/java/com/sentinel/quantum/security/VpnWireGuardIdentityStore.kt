package com.sentinel.quantum.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.wireguard.crypto.Key
import com.wireguard.crypto.KeyPair
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Dedicated WireGuard identity for the public Internet VPN.
 *
 * This is intentionally separate from the private-mesh WireGuard identity so a key compromise,
 * rotation or revocation in one trust domain does not implicitly affect the other.
 */
class VpnWireGuardIdentityStore(context: Context) {
    data class Identity(
        val publicKeyBase64: String,
        val publicKeyFingerprint: String
    )

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun getOrCreateIdentity(): Identity {
        loadPublicIdentity()?.let { identity ->
            if (loadPrivateKeyBase64() != null) return identity
            clear()
        }

        val pair = KeyPair()
        val privateKey = pair.privateKey.toBase64()
        val publicKey = pair.publicKey.toBase64()
        persistPrivateKey(privateKey, publicKey)
        return Identity(publicKey, fingerprint(publicKey))
    }

    @Synchronized
    fun loadPublicIdentity(): Identity? {
        val publicKey = prefs.getString(KEY_PUBLIC, null) ?: return null
        if (!validWireGuardKey(publicKey)) return null
        return Identity(publicKey, fingerprint(publicKey))
    }

    /**
     * Returns plaintext only for immediate in-memory WireGuard configuration construction.
     * Callers must not log, persist or expose it.
     */
    @Synchronized
    fun loadPrivateKeyBase64(): String? {
        val ivEncoded = prefs.getString(KEY_IV, null) ?: return null
        val ciphertextEncoded = prefs.getString(KEY_CIPHERTEXT, null) ?: return null
        val publicKey = prefs.getString(KEY_PUBLIC, null) ?: return null
        if (!validWireGuardKey(publicKey)) return null

        return runCatching {
            val iv = Base64.decode(ivEncoded, Base64.NO_WRAP)
            val ciphertext = Base64.decode(ciphertextEncoded, Base64.NO_WRAP)
            require(iv.size in 12..16) { "VPN_WG_IV_INVALID" }
            require(ciphertext.size in 17..256) { "VPN_WG_CIPHERTEXT_INVALID" }

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateStorageKey(),
                GCMParameterSpec(GCM_TAG_BITS, iv)
            )
            val plaintext = cipher.doFinal(ciphertext)
            try {
                val privateKey = plaintext.toString(Charsets.US_ASCII)
                require(validWireGuardKey(privateKey)) { "VPN_WG_PRIVATE_KEY_INVALID" }
                val derived = KeyPair(Key.fromBase64(privateKey)).publicKey.toBase64()
                require(derived == publicKey) { "VPN_WG_PUBLIC_KEY_MISMATCH" }
                privateKey
            } finally {
                plaintext.fill(0)
            }
        }.getOrNull()
    }

    @Synchronized
    fun rotate(): Identity {
        clear()
        return getOrCreateIdentity()
    }

    @Synchronized
    fun clear() {
        prefs.edit().clear().commit()
        runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
        }
    }

    @Synchronized
    private fun persistPrivateKey(privateKeyBase64: String, publicKeyBase64: String) {
        require(validWireGuardKey(privateKeyBase64)) { "VPN_WG_PRIVATE_KEY_INVALID" }
        require(validWireGuardKey(publicKeyBase64)) { "VPN_WG_PUBLIC_KEY_INVALID" }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateStorageKey())
        val plaintext = privateKeyBase64.toByteArray(Charsets.US_ASCII)
        val ciphertext = try {
            cipher.doFinal(plaintext)
        } finally {
            plaintext.fill(0)
        }

        require(
            prefs.edit()
                .putString(KEY_PUBLIC, publicKeyBase64)
                .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .putString(KEY_CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
                .commit()
        ) { "VPN_WG_IDENTITY_STORE_FAILED" }
    }

    @Synchronized
    private fun getOrCreateStorageKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        internal fun validWireGuardKey(value: String): Boolean = runCatching {
            if (value.length != 44) return false
            Key.fromBase64(value)
            true
        }.getOrDefault(false)

        internal fun fingerprint(publicKeyBase64: String): String {
            require(validWireGuardKey(publicKeyBase64)) { "VPN_WG_PUBLIC_KEY_INVALID" }
            return MessageDigest.getInstance("SHA-256")
                .digest(publicKeyBase64.toByteArray(Charsets.US_ASCII))
                .joinToString("") { "%02x".format(it) }
        }

        private const val PREFS_NAME = "sentinel_vpn_wireguard_identity"
        private const val KEY_PUBLIC = "public_key"
        private const val KEY_IV = "private_key_iv"
        private const val KEY_CIPHERTEXT = "private_key_ciphertext"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "sentinel_vpn_wireguard_identity_aes_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
    }
}
