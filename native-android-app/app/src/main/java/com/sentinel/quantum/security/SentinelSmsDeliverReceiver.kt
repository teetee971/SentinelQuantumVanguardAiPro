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
        if (messages.isEmpty() || messages.size > MAX_SMS_PARTS) {
            LocalLogger(context).log(
                LocalLogger.LogLevel.WARNING,
                "DefaultSms",
                "SMS entrant rejeté : nombre de parties invalide"
            )
            return
        }

        val address = messages.firstNotNullOfOrNull { it.originatingAddress }
            .orEmpty()
            .trim()
            .take(MAX_ADDRESS_CHARS)
        val body = buildString {
            messages.forEach { message ->
                val remaining = SentinelSmsSender.MAX_BODY_CHARS - length
                if (remaining <= 0) return@forEach
                append(message.messageBody.orEmpty().take(remaining))
            }
        }
        if (address.isBlank() || body.isBlank()) return

        val receivedAt = System.currentTimeMillis()
        val sentAt = messages
            .map { it.timestampMillis }
            .filter { it > 0L }
            .minOrNull()
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, receivedAt)
            sentAt?.let { put(Telephony.Sms.DATE_SENT, it) }
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
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
            PhonePrivateTimelineStore(context).append(
                PhonePrivateTimeline.Event(
                    kind = PhonePrivateTimeline.Kind.SMS,
                    timestampMs = receivedAt,
                    direction = "INCOMING",
                    signal = PhoneCorePhysicalValidation.SIGNAL_SMS_RECEIVED
                )
            )
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

    private companion object {
        const val MAX_SMS_PARTS = 32
        const val MAX_ADDRESS_CHARS = 128
    }
}
