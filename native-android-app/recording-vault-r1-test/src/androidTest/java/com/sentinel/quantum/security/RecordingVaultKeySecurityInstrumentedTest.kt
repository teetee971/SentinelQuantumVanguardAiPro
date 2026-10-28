package com.sentinel.quantum.security

import android.os.Build
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.security.KeyFactory
import java.security.KeyStore
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecordingVaultKeySecurityInstrumentedTest {

    @Test
    fun reportActualKeySecurityLevel() {
        // Generate the key only if it does not already exist.
        RecordingVaultCrypto.encrypt("R1 key audit".toByteArray())

        val store = KeyStore.getInstance("AndroidKeyStore")
        store.load(null)

        val key = store.getKey(
            "sentinel_recording_vault_r1", null
        ) as SecretKey

        val factory = SecretKeyFactory.getInstance(
            key.algorithm, "AndroidKeyStore"
        )

        val info = factory.getKeySpec(
            key, KeyInfo::class.java
        ) as KeyInfo

        assertEquals(256, info.keySize)

        val level = if (Build.VERSION.SDK_INT >= 31) {
            when (info.securityLevel) {
                KeyProperties.SECURITY_LEVEL_STRONGBOX -> "STRONGBOX"
                KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> "TRUSTED_ENVIRONMENT"
                KeyProperties.SECURITY_LEVEL_SOFTWARE -> "SOFTWARE"
                else -> "OTHER_${info.securityLevel}"
            }
        } else {
            if (info.isInsideSecureHardware) "HARDWARE" else "SOFTWARE"
        }

        Log.i("SentinelR1KeyAudit", "KEY_SECURITY_LEVEL=$level")
        Log.i("SentinelR1KeyAudit", "KEY_SIZE=${info.keySize}")
        Log.i(
            "SentinelR1KeyAudit",
            "USER_AUTH_REQUIRED=${info.isUserAuthenticationRequired}"
        )

        assertTrue(level.isNotBlank())
    }
}
