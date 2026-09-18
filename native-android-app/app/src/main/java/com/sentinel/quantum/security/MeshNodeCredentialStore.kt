package com.sentinel.quantum.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class MeshNodeCredentialStore(context: Context) {
    data class Credential(val nodeId: String, val token: String)

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun save(nodeId: String, token: String) {
        require(validNodeId(nodeId)) { "MESH_NODE_ID_INVALID" }
        require(token.length in MIN_TOKEN_CHARS..MAX_TOKEN_CHARS) { "MESH_NODE_TOKEN_INVALID" }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(token.toByteArray(Charsets.UTF_8))

        prefs.edit()
            .putString(KEY_NODE_ID, nodeId)
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(KEY_CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .commit()
    }

    @Synchronized
    fun load(): Credential? {
        val nodeId = prefs.getString(KEY_NODE_ID, null) ?: return null
        val ivEncoded = prefs.getString(KEY_IV, null) ?: return null
        val ciphertextEncoded = prefs.getString(KEY_CIPHERTEXT, null) ?: return null
        if (!validNodeId(nodeId)) return null

        return runCatching {
            val iv = Base64.decode(ivEncoded, Base64.NO_WRAP)
            val ciphertext = Base64.decode(ciphertextEncoded, Base64.NO_WRAP)
            require(iv.size in 12..16) { "MESH_NODE_IV_INVALID" }
            require(ciphertext.size in 17..512) { "MESH_NODE_CIPHERTEXT_INVALID" }

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(GCM_TAG_BITS, iv)
            )
            val tokenBytes = cipher.doFinal(ciphertext)
            try {
                val token = tokenBytes.toString(Charsets.UTF_8)
                require(token.length in MIN_TOKEN_CHARS..MAX_TOKEN_CHARS) { "MESH_NODE_TOKEN_INVALID" }
                Credential(nodeId, token)
            } finally {
                tokenBytes.fill(0)
            }
        }.getOrNull()
    }

    @Synchronized
    fun clear() {
        prefs.edit().clear().commit()
        runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
        }
    }

    fun hasCredential(): Boolean = load() != null

    @Synchronized
    private fun getOrCreateKey(): SecretKey {
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
        internal const val MIN_TOKEN_CHARS = 32
        internal const val MAX_TOKEN_CHARS = 128

        internal fun validNodeId(value: String): Boolean =
            value.length in 2..256 && value.matches(Regex("[A-Za-z0-9:_./-]+"))

        private const val PREFS_NAME = "sentinel_mesh_node_credentials"
        private const val KEY_NODE_ID = "node_id"
        private const val KEY_IV = "token_iv"
        private const val KEY_CIPHERTEXT = "token_ciphertext"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "sentinel_mesh_node_credential_aes_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
    }
}
