package com.sentinel.quantum.security

import android.Manifest
import android.app.PendingIntent
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.PhoneNumberUtils
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

/**
 * Minimal real SMS sending primitive for the future default-SMS client.
 * It refuses to send unless Sentinel actually holds ROLE_SMS and SEND_SMS is granted.
 */
class SentinelSmsSender(private val context: Context) {

    data class SendResult(val accepted: Boolean, val reason: String, val subscriptionId: Int? = null)

    fun send(destination: String, body: String, requestedSubscriptionId: Int? = null): SendResult {
        val normalized = sanitizeDestination(destination) ?: return SendResult(false, "INVALID_DESTINATION")
        if (body.isBlank() || body.length > MAX_BODY_CHARS) {
            return SendResult(false, "INVALID_MESSAGE")
        }
        if (!holdsSmsRole()) return SendResult(false, "SMS_ROLE_NOT_HELD")
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            return SendResult(false, "SEND_SMS_PERMISSION_NOT_GRANTED")
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return SendResult(false, "READ_PHONE_STATE_PERMISSION_NOT_GRANTED")
        }
        if (isEmergencyNumber(normalized)) {
            return SendResult(false, "EMERGENCY_NUMBER_USE_DIALER")
        }

        return try {
            val subscriptionManager = context.getSystemService(SubscriptionManager::class.java)
            val activeIds = runCatching {
                subscriptionManager.activeSubscriptionInfoList
                    .orEmpty()
                    .map { it.subscriptionId }
                    .filter { it != SubscriptionManager.INVALID_SUBSCRIPTION_ID }
                    .toSet()
            }.getOrElse { return SendResult(false, "SMS_SUBSCRIPTION_LOOKUP_FAILED") }
            val defaultId = SubscriptionManager.getDefaultSmsSubscriptionId()
                .takeUnless { it == SubscriptionManager.INVALID_SUBSCRIPTION_ID }
            val selection = SmsSubscriptionSelectionPolicy.select(
                activeSubscriptionIds = activeIds,
                requestedSubscriptionId = requestedSubscriptionId,
                defaultSubscriptionId = defaultId
            )
            if (!selection.accepted || selection.subscriptionId == null) {
                return SendResult(false, selection.reason)
            }
            val subscriptionId = selection.subscriptionId

            @Suppress("DEPRECATION")
            val manager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
                    .createForSubscriptionId(subscriptionId)
            } else {
                SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
            }

            val parts = manager.divideMessage(body)
            val sent = PendingIntent.getBroadcast(
                context,
                normalized.hashCode(),
                Intent(ACTION_SENT).setPackage(context.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val delivered = PendingIntent.getBroadcast(
                context,
                normalized.hashCode() xor 0x5a5a,
                Intent(ACTION_DELIVERED).setPackage(context.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (parts.size <= 1) {
                manager.sendTextMessage(normalized, null, body, sent, delivered)
            } else {
                manager.sendMultipartTextMessage(
                    normalized,
                    null,
                    ArrayList(parts),
                    ArrayList(List(parts.size) { sent }),
                    ArrayList(List(parts.size) { delivered })
                )
            }
            SendResult(true, "SUBMITTED_TO_ANDROID_TELEPHONY", subscriptionId)
        } catch (_: Exception) {
            SendResult(false, "TELEPHONY_SEND_FAILED")
        }
    }

    private fun sanitizeDestination(raw: String): String? {
        val value = raw.trim()
        if (value.isEmpty() || value.length > 32) return null
        if (value.count { it == '+' } > 1 || ('+' in value && !value.startsWith("+"))) return null
        if (!value.all { it.isDigit() || it in "+*#" }) return null
        return value
    }

    private fun isEmergencyNumber(number: String): Boolean {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.getSystemService(TelephonyManager::class.java).isEmergencyNumber(number)
            } else {
                @Suppress("DEPRECATION")
                PhoneNumberUtils.isEmergencyNumber(number)
            }
        }.getOrDefault(false)
    }

    fun holdsSmsRole(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val roleManager = context.getSystemService(RoleManager::class.java)
        return roleManager.isRoleAvailable(RoleManager.ROLE_SMS) &&
            roleManager.isRoleHeld(RoleManager.ROLE_SMS)
    }

    companion object {
        const val ACTION_SENT = "com.sentinel.quantum.SMS_SENT"
        const val ACTION_DELIVERED = "com.sentinel.quantum.SMS_DELIVERED"
        const val MAX_BODY_CHARS = 20_000
    }
}
