package com.sentinel.quantum.security

import android.Manifest
import android.content.Context
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.PermissionChecker
import java.util.Locale

/**
 * Home regions captured outside the screening callback.
 * A national number is folded with the French plan only when the selected region is French.
 * Anywhere else it is parsed for that region, and only after the offline metadata is loaded.
 * Until then, or if the number is not valid there, the call rings.
 *
 * [installedRegions] lists every readable SIM country. The screening callback does not
 * learn which line received the call, so more than one region stays an inference.
 */
object ScreeningHomeRegion {
    private val frenchIso = setOf("FR", "GP", "GF", "MQ", "RE", "YT", "BL", "MF", "PM")

    @Volatile
    private var iso: String = "FR"

    @Volatile
    private var installed: Set<String> = setOf("FR")

    fun current(): String = iso

    fun installedRegions(): Set<String> = installed

    fun usesFrenchNationalPlan(): Boolean = iso in frenchIso

    fun capture(context: Context) {
        val app = context.applicationContext
        val fromSims = readSimRegions(app)
        val fallback = fallbackRegion(app)
        installed = if (fromSims.isEmpty()) setOf(fallback) else fromSims
        iso = when (fromSims.size) {
            1 -> fromSims.first()
            else -> fallback
        }
    }

    private fun fallbackRegion(context: Context): String {
        val sim = runCatching {
            context.getSystemService(TelephonyManager::class.java)?.simCountryIso
        }.getOrNull()
        return sim?.trim()?.takeIf { it.length == 2 }?.uppercase(Locale.US)
            ?: Locale.getDefault().country.takeIf { it.length == 2 }?.uppercase(Locale.US)
            ?: "FR"
    }

    private fun readSimRegions(context: Context): Set<String> {
        if (PermissionChecker.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) !=
            PermissionChecker.PERMISSION_GRANTED
        ) {
            return emptySet()
        }
        val telephony = context.getSystemService(TelephonyManager::class.java) ?: return emptySet()
        val subscriptions = runCatching {
            context.getSystemService(SubscriptionManager::class.java)?.activeSubscriptionInfoList
        }.getOrNull().orEmpty()
        return subscriptions.mapNotNull { info ->
            runCatching { telephony.createForSubscriptionId(info.subscriptionId).simCountryIso }
                .getOrNull()
                ?.trim()
                ?.takeIf { it.length == 2 }
                ?.uppercase(Locale.US)
        }.toSet()
    }
}
