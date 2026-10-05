package com.sentinel.quantum.security

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telecom.TelecomManager

/**
 * Hands an emergency dial request to Android's system/default phone surface when Sentinel cannot
 * safely place it itself (for example after ROLE_DIALER or CALL_PHONE has been revoked).
 *
 * This helper never places the call and never chooses a SIM. It only opens an external dialer with
 * the already user-entered number so Android/system telephony can own emergency routing.
 */
object EmergencyCallHandoff {
    fun openSystemDialer(context: Context, number: String): Boolean {
        if (number.isBlank()) return false
        val telecom = context.getSystemService(TelecomManager::class.java)
        val preferredPackage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            telecom.systemDialerPackage
                ?.takeIf { it.isNotBlank() && it != context.packageName }
                ?: telecom.defaultDialerPackage
                    ?.takeIf { it.isNotBlank() && it != context.packageName }
        } else {
            telecom.defaultDialerPackage
                ?.takeIf { it.isNotBlank() && it != context.packageName }
        }

        val intent = Intent(
            Intent.ACTION_DIAL,
            Uri.parse("tel:" + Uri.encode(number))
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (preferredPackage != null) intent.setPackage(preferredPackage)

        val resolvedPackage = runCatching {
            context.packageManager
                .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo
                ?.packageName
        }.getOrNull() ?: return false
        if (resolvedPackage == context.packageName) return false

        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}
