package com.sentinel.quantum.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** Trusted composition only, called off the main thread. Not initialized by Application.onCreate.
 * An existing Keystore alias requires the original journal; missing files never cause regeneration.
 * HMAC is storage authentication, NOT human authentication or permission to execute an action.
 */
internal object SentinelAiAndroidAuditJournal {
    private const val ALIAS = "sentinel_ai_audit_v1"
    @Synchronized fun open(context: Context): SentinelAiAuditJournal {
        val root = context.applicationContext.noBackupFilesDir
        FileOutputStream(File(root, "sentinel-ai-audit-key.lock"), true).channel.use { channel ->
            channel.lock().use {
                val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                val directory = File(root, "sentinel-ai-audit-v1")
                if (store.containsAlias(ALIAS)) {
                    val key = store.getKey(ALIAS, null) as? SecretKey ?: throw IOException("Audit key unavailable")
                    return SentinelAiAuditJournal.open(directory, key)
                }
                // Orphaned data may be from an interrupted initialization. Never overwrite it.
                if (directory.exists()) throw IOException("Audit initialization inconsistent")
                val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore")
                generator.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                    .setDigests(KeyProperties.DIGEST_SHA256).build())
                return SentinelAiAuditJournal.create(directory, generator.generateKey())
            }
        }
    }
}
