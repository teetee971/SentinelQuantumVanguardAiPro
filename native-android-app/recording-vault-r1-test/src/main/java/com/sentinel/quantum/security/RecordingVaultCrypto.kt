package com.sentinel.quantum.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal object RecordingVaultCrypto {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "sentinel_recording_vault_r1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    @Synchronized
    private fun key(createIfMissing: Boolean = true): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply {
            load(null)
        }

        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let {
            return it
        }

        check(createIfMissing) { "Recording Vault key is missing" }
        check(!store.containsAlias(KEY_ALIAS)) { "Recording Vault alias is not a secret key" }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE
        )

        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or
                    KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(
                    KeyProperties.ENCRYPTION_PADDING_NONE
                )
                .setKeySize(256)
                .build()
        )

        return generator.generateKey()
    }

    fun encrypt(data: ByteArray): ByteArray {
        require(data.isNotEmpty())

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())

        val iv = cipher.iv
        require(iv.size == IV_BYTES)

        val encrypted = cipher.doFinal(data)

        return byteArrayOf(1) + iv + encrypted
    }

    fun decrypt(data: ByteArray): ByteArray {
        require(data.size >= 1 + IV_BYTES + 16)
        require(data[0] == 1.toByte())

        val iv = data.copyOfRange(1, 1 + IV_BYTES)
        val encrypted = data.copyOfRange(
            1 + IV_BYTES,
            data.size
        )

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(createIfMissing = false),
            GCMParameterSpec(TAG_BITS, iv)
        )

        return cipher.doFinal(encrypted)
    }

    private fun aad(directory: String, id: String): ByteArray {
        require(Regex("[A-Za-z0-9_-]{1,64}").matches(directory))
        require(Regex("[A-Za-z0-9_-]{1,64}").matches(id))
        val domain = "sentinel.recording-vault.v2"
        return "$domain:${directory.length}:$directory:${id.length}:$id"
            .toByteArray(Charsets.UTF_8)
    }

    fun encryptV2(data: ByteArray, directory: String, id: String): ByteArray {
        require(data.isNotEmpty())
        val associatedData = aad(directory, id)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(associatedData)
        val iv = cipher.iv
        require(iv.size == IV_BYTES)
        return byteArrayOf(2) + iv + cipher.doFinal(data)
    }

    fun decryptV2(data: ByteArray, directory: String, id: String): ByteArray {
        require(data.size >= 1 + IV_BYTES + 16)
        require(data[0] == 2.toByte())
        val associatedData = aad(directory, id)
        val iv = data.copyOfRange(1, 1 + IV_BYTES)
        val encrypted = data.copyOfRange(1 + IV_BYTES, data.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(createIfMissing = false),
            GCMParameterSpec(TAG_BITS, iv)
        )
        cipher.updateAAD(associatedData)
        return cipher.doFinal(encrypted)
    }
}
