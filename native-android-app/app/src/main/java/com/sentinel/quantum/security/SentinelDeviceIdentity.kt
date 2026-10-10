package com.sentinel.quantum.security

import android.content.Context
import android.provider.Settings
import java.security.MessageDigest

/** Stable per-installation Android identifier used only for backend device binding. */
object SentinelDeviceIdentity {
    fun current(context: Context): String? = stableIdFor(
        Settings.Secure.getString(
            context.applicationContext.contentResolver,
            Settings.Secure.ANDROID_ID
        )
    )

    fun stableIdFor(androidId: String?): String? {
        val normalized = androidId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(Charsets.UTF_8))
        return "android-" + digest.joinToString("") { byte -> "%02x".format(byte) }
    }
}
