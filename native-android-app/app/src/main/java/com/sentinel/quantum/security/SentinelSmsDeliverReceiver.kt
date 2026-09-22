package com.sentinel.quantum.security

import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony

/**
 * Receives SMS_DELIVER only when Android routes the default-SMS broadcast to Sentinel.
 * Raw message content is written to the Android SMS provider; logs never contain the body.
 */
class SentinelSmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        if (!holdsSmsRole(context)) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) return

        val address = messages.firstNotNullOfOrNull { it.originatingAddress }.orEmpty().take(64)
        val body = buildString {
            messages.forEach { append(it.messageBody.orEmpty()) }
        }.take(SentinelSmsSender.MAX_BODY_CHARS)
        if (address.isBlank() || body.isBlank()) return

        val receivedAt = messages.minOfOrNull { it.timestampMillis } ?: System.currentTimeMillis()
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, receivedAt)
            put(Telephony.Sms.DATE_SENT, receivedAt)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
            val subscriptionId = intent.getIntExtra("subscription", -1)
            if (subscriptionId >= 0) put(Telephony.Sms.SUBSCRIPTION_ID, subscriptionId)
        }

        val inserted = runCatching {
            context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
        }.getOrNull()

        val logger = LocalLogger(context)
        if (inserted != null) {
            val smsAnalysis = SmsLinkAnalyzer(logger).analyze(body)
            SmsTimelineMapper.toEvent(smsAnalysis)?.let { event ->
                PhonePrivateTimelineStore(context).append(event)
            }
            SmsNotificationHelper.notifyMessage(
                context,
                title = address,
                preview = body,
                notificationId = (receivedAt xor address.hashCode().toLong()).toInt()
            )
            logger.log(
                LocalLogger.LogLevel.SECURITY,
                "DefaultSms",
                "SMS entrant enregistré; analyse locale=" + smsAnalysis.riskLevel.name +
                    "; liens=" + smsAnalysis.linksInspected
            )
        } else {
            logger.log(
                LocalLogger.LogLevel.WARNING,
                "DefaultSms",
                "Échec d'enregistrement d'un SMS entrant"
            )
        }
    }

    private fun holdsSmsRole(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val manager = context.getSystemService(RoleManager::class.java)
            manager.isRoleAvailable(RoleManager.ROLE_SMS) &&
                manager.isRoleHeld(RoleManager.ROLE_SMS)
        } else {
            Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
        }
    }
}
