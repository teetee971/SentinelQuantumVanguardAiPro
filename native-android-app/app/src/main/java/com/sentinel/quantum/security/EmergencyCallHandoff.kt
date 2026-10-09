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
        val telecom = context.getSystemService(TelecomManager::class.java) ?: return false
        val intent = Intent(
            Intent.ACTION_DIAL,
            Uri.parse("tel:" + Uri.encode(number))
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val preferredPackage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            telecom.systemDialerPackage
                ?.takeIf { it.isNotBlank() && it != context.packageName }
                ?: telecom.defaultDialerPackage
                    ?.takeIf { it.isNotBlank() && it != context.packageName }
        } else {
            telecom.defaultDialerPackage
                ?.takeIf { it.isNotBlank() && it != context.packageName }
                ?: findLegacySystemDialer(context, intent)
        } ?: return false

        // Keep emergency handoff explicit. If Sentinel is the selected default dialer on API 24-28,
        // an implicit ACTION_DIAL would otherwise resolve straight back to Sentinel and defeat the
        // safety handoff. MATCH_SYSTEM_ONLY is available from API 24, which is this app's minSdk.
        intent.setPackage(preferredPackage)
        val resolvedPackage = runCatching {
            context.packageManager
                .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo
                ?.packageName
        }.getOrNull() ?: return false
        if (resolvedPackage != preferredPackage || resolvedPackage == context.packageName) return false

        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private fun findLegacySystemDialer(context: Context, dialIntent: Intent): String? = runCatching {
        context.packageManager
            .queryIntentActivities(
                dialIntent,
                PackageManager.MATCH_DEFAULT_ONLY or PackageManager.MATCH_SYSTEM_ONLY
            )
            .asSequence()
            .mapNotNull { it.activityInfo?.packageName }
            .firstOrNull { it.isNotBlank() && it != context.packageName }
    }.getOrNull()
}
