package com.sentinel.quantum.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Notification actions for the active Telecom call. */
class SentinelCallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            SentinelCallNotificationHelper.ACTION_ANSWER -> {
                if (SentinelInCallService.answer()) {
                    SentinelCallNotificationHelper.cancel(context)
                }
            }
            SentinelCallNotificationHelper.ACTION_REJECT -> {
                if (SentinelInCallService.reject()) {
                    SentinelCallNotificationHelper.cancel(context)
                }
            }
        }
    }
}
