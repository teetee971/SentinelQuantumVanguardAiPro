package com.sentinel.quantum.security

import android.content.Context
import android.provider.Settings
import java.security.MessageDigest

/**
 * Stable, hashed Android identifier used for backend device binding.
 *
 * On Android 8+, ANDROID_ID is scoped to the app signing key, device and user,
 * not to an individual installation. It can survive uninstall/reinstall and
 * must not be treated as proof of a fresh enrollment or as a revocation token.
 */
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
