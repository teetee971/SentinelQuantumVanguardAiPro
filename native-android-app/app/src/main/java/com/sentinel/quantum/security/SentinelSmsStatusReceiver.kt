package com.sentinel.quantum.security

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Records only delivery state; no destination or message body is logged. */
class SentinelSmsStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = when (intent.action) {
            SentinelSmsSender.ACTION_SENT ->
                if (resultCode == Activity.RESULT_OK) "SENT_OK" else "SENT_ERROR_" + resultCode
            SentinelSmsSender.ACTION_DELIVERED ->
                if (resultCode == Activity.RESULT_OK) "DELIVERED_OK" else "DELIVERY_ERROR_" + resultCode
            else -> return
        }
        LocalLogger(context).log(LocalLogger.LogLevel.SECURITY, "DefaultSms", event)
        PhonePrivateTimelineStore(context).append(
            PhonePrivateTimeline.Event(
                kind = PhonePrivateTimeline.Kind.SMS,
                timestampMs = System.currentTimeMillis(),
                direction = "OUTGOING",
                signal = event
            )
        )
    }
}
