package com.sentinel.quantum.security

import android.content.Context
import android.telephony.TelephonyManager
import java.util.Locale

/**
 * Home region captured outside the screening callback.
 * A national number is folded with the French plan only when this region is French.
 * Anywhere else, a national number that is not already international stays unmatched and rings.
 */
object ScreeningHomeRegion {
    private val frenchIso = setOf("FR", "GP", "GF", "MQ", "RE", "YT", "BL", "MF", "PM")

    @Volatile
    private var iso: String = "FR"

    fun current(): String = iso

    fun usesFrenchNationalPlan(): Boolean = iso in frenchIso

    fun capture(context: Context) {
        val sim = runCatching {
            context.applicationContext.getSystemService(TelephonyManager::class.java)?.simCountryIso
        }.getOrNull()
        iso = sim?.trim()?.takeIf { it.length == 2 }?.uppercase(Locale.US)
            ?: Locale.getDefault().country.takeIf { it.length == 2 }?.uppercase(Locale.US)
            ?: "FR"
    }
}
