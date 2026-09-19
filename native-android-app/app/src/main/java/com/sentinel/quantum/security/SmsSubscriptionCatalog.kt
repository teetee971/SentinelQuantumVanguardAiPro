package com.sentinel.quantum.security

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat

/**
 * Minimal local catalog for explicit multi-SIM SMS selection.
 * Does not read phone numbers, ICCIDs, contacts, or transmit subscription data.
 */
class SmsSubscriptionCatalog(private val context: Context) {
    data class Line(val subscriptionId: Int, val label: String)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED

    fun activeLines(): List<Line> {
        if (!hasPermission()) return emptyList()
        return try {
            context.getSystemService(SubscriptionManager::class.java)
                .activeSubscriptionInfoList
                .orEmpty()
                .filter { it.subscriptionId >= 0 }
                .map { info ->
                    val label = info.displayName?.toString()?.trim().orEmpty()
                    Line(info.subscriptionId, label.ifBlank { "SIM " + (info.simSlotIndex + 1) })
                }
                .distinctBy { it.subscriptionId }
                .sortedBy { it.subscriptionId }
        } catch (_: SecurityException) {
            emptyList()
        } catch (_: UnsupportedOperationException) {
            emptyList()
        }
    }
}
