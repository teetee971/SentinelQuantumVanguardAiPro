package com.sentinel.quantum.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

class SentinelSmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        if (!SmsProviderStore.isDefaultSmsHandler(context)) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) return
        val address = messages.firstNotNullOfOrNull { it.originatingAddress } ?: "Expéditeur inconnu"
        val body = messages.joinToString(separator = "") { it.messageBody.orEmpty() }
            .take(SmsProviderStore.MAX_BODY_LENGTH)
        val timestamp = messages.minOfOrNull { it.timestampMillis } ?: System.currentTimeMillis()
        val subscriptionId = intent.getIntExtra("subscription", -1).takeIf { it >= 0 }
        if (SmsProviderStore.insertIncomingSms(context, address, body, timestamp, subscriptionId)) {
            SmsNotificationHelper.notifyMessage(
                context,
                title = address,
                preview = body,
                notificationId = (timestamp xor address.hashCode().toLong()).toInt()
            )
        }
    }
}
