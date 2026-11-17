package com.sentinel.quantum.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal object SentinelAiPairingStore {
    private const val ALIAS = "sentinel_ai_pairing_aes_v1"

    private fun file(context: Context) = AtomicFile(
        File(context.applicationContext.noBackupFilesDir, "sentinel-ai-pairing-v1")
    )

    fun exists(context: Context): Boolean {
        val base = file(context).baseFile
        return base.exists() || File(base.path + ".bak").exists()
    }

    private fun store() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun key(create: Boolean): SecretKey {
        val store = store()
        if (store.containsAlias(ALIAS)) {
            return store.getKey(ALIAS, null) as? SecretKey
                ?: error("Pairing key unavailable")
        }
        check(create) { "Pairing key unavailable" }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(
                ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build())
            generateKey()
        }
    }

    @Synchronized fun save(context: Context, pairing: ByteArray) {
        require(pairing.size == 32)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(true))
        val iv = cipher.iv
        check(iv.size == 12)
        val encrypted = cipher.doFinal(pairing)
        val target = file(context)
        val output = target.startWrite()
        try {
            output.write(byteArrayOf(1))
            output.write(iv)
            output.write(encrypted)
            target.finishWrite(output)
        } catch (error: Exception) {
            target.failWrite(output)
            throw error
        }
    }

    @Synchronized fun load(context: Context): ByteArray {
        val data = file(context).openRead().use { input ->
            val buffer = ByteArray(62)
            var count = 0
            while (count < buffer.size) {
                val n = input.read(buffer, count, buffer.size - count)
                if (n < 0) break
                count += n
            }
            buffer.copyOf(count)
        }
        check(data.size == 61 && data[0] == 1.toByte()) { "Pairing data rejected" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(false),
            GCMParameterSpec(128, data.copyOfRange(1, 13)))
        return cipher.doFinal(data.copyOfRange(13, 61)).also {
            check(it.size == 32)
        }
    }

    @Synchronized fun forget(context: Context) {
        val store = store()
        if (store.containsAlias(ALIAS)) store.deleteEntry(ALIAS)
        file(context).delete()
        check(!exists(context)) { "Pairing deletion failed" }
    }
}
