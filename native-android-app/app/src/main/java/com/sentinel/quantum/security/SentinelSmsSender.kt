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
import android.net.Uri
import android.provider.Telephony
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicInteger

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
            val sendToken = nextRequestToken()
            fun statusIntent(action: String, partIndex: Int, delivered: Boolean): PendingIntent {
                val callbackKind = if (delivered) "delivered" else "sent"
                return PendingIntent.getBroadcast(
                    context,
                    requestCode(sendToken, partIndex, delivered),
                    Intent(action)
                        .setPackage(context.packageName)
                        .setData(Uri.parse("sentinel-sms-status://callback/$sendToken/$partIndex/$callbackKind"))
                        .putExtra(EXTRA_SEND_TOKEN, sendToken)
                        .putExtra(EXTRA_PART_INDEX, partIndex)
                        .putExtra(EXTRA_PART_COUNT, parts.size),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }
            if (parts.size <= 1) {
                manager.sendTextMessage(
                    normalized, null, body,
                    statusIntent(ACTION_SENT, 0, false),
                    statusIntent(ACTION_DELIVERED, 0, true)
                )
            } else {
                manager.sendMultipartTextMessage(
                    normalized,
                    null,
                    ArrayList(parts),
                    ArrayList(parts.indices.map { statusIntent(ACTION_SENT, it, false) }),
                    ArrayList(parts.indices.map { statusIntent(ACTION_DELIVERED, it, true) })
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
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java) ?: return false
            roleManager.isRoleAvailable(RoleManager.ROLE_SMS) &&
                roleManager.isRoleHeld(RoleManager.ROLE_SMS)
        } else {
            Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
        }
    }

    companion object {
        const val ACTION_SENT = "com.sentinel.quantum.SMS_SENT"
        const val ACTION_DELIVERED = "com.sentinel.quantum.SMS_DELIVERED"
        const val MAX_BODY_CHARS = 20_000
        const val EXTRA_SEND_TOKEN = "sms.send_token"
        const val EXTRA_PART_INDEX = "sms.part_index"
        const val EXTRA_PART_COUNT = "sms.part_count"
        private val requestSequence = AtomicInteger(1)

        private fun nextRequestToken(): Int = requestSequence.getAndUpdate { current ->
            if (current == Int.MAX_VALUE) 1 else current + 1
        }

        private fun requestCode(token: Int, partIndex: Int, delivered: Boolean): Int {
            var value = 31 * token + partIndex
            if (delivered) value = value xor 0x5a5a5a5a
            return value
        }
    }
}
