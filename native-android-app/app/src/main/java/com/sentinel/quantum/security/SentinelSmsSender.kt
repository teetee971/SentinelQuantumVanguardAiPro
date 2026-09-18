package com.sentinel.quantum.security

import android.Manifest
import android.app.PendingIntent
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat

/**
 * Minimal real SMS sending primitive for the future default-SMS client.
 * It refuses to send unless Sentinel actually holds ROLE_SMS and SEND_SMS is granted.
 */
class SentinelSmsSender(private val context: Context) {

    data class SendResult(val accepted: Boolean, val reason: String)

    fun send(destination: String, body: String): SendResult {
        val normalized = destination.trim()
        if (normalized.isEmpty() || normalized.length > 32 || body.isBlank() || body.length > MAX_BODY_CHARS) {
            return SendResult(false, "INVALID_MESSAGE")
        }
        if (!holdsSmsRole()) return SendResult(false, "SMS_ROLE_NOT_HELD")
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            return SendResult(false, "SEND_SMS_PERMISSION_NOT_GRANTED")
        }

        return try {
            val subscriptionId = SubscriptionManager.getDefaultSmsSubscriptionId()
            @Suppress("DEPRECATION")
            val manager = if (subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
            } else {
                SmsManager.getDefault()
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
            SendResult(true, "SUBMITTED_TO_ANDROID_TELEPHONY")
        } catch (_: Exception) {
            SendResult(false, "TELEPHONY_SEND_FAILED")
        }
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
