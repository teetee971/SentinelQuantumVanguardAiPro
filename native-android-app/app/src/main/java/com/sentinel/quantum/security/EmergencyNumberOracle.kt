package com.sentinel.quantum.security

import android.content.Context
import android.os.Build
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager

/**
 * Android-owned emergency-number oracle across every Phone Core API level.
 *
 * Never maintains a Sentinel country-number catalogue. API 29+ uses TelephonyManager as the
 * authoritative public API. API 24-28 uses the platform's then-public local emergency lookup.
 * If the modern oracle is temporarily unreadable, the legacy platform oracle is used as a
 * compatibility fallback instead of immediately treating a possible emergency as ordinary.
 */
object EmergencyNumberOracle {
    fun isEmergency(context: Context, number: String): Boolean {
        if (number.isBlank()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val modern = runCatching {
                context.getSystemService(TelephonyManager::class.java).isEmergencyNumber(number)
            }.getOrNull()
            if (modern != null) return modern
        }
        return legacyIsEmergency(context, number)
    }

    @Suppress("DEPRECATION")
    private fun legacyIsEmergency(context: Context, number: String): Boolean =
        runCatching {
            PhoneNumberUtils.isLocalEmergencyNumber(context, number)
        }.getOrDefault(false)
}
