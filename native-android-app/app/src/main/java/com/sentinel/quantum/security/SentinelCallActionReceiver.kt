package com.sentinel.quantum.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Notification actions bound to the exact Telecom call that created the notification. */
class SentinelCallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val callbackUri = intent.data ?: return
        if (
            callbackUri.scheme != ACTION_URI_SCHEME ||
            callbackUri.host != ACTION_URI_HOST
        ) return

        val path = callbackUri.pathSegments
        if (path.size != 2) return
        val callId = path[0].takeIf { CALL_ID.matches(it) } ?: return
        val kind = path[1]
        if (intent.getStringExtra(SentinelCallNotificationHelper.EXTRA_CALL_ID) != callId) return

        val handled = when {
            intent.action == SentinelCallNotificationHelper.ACTION_ANSWER && kind == "answer" ->
                SentinelInCallService.answer(callId)
            intent.action == SentinelCallNotificationHelper.ACTION_REJECT && kind == "reject" ->
                SentinelInCallService.reject(callId)
            else -> false
        }

        if (handled) {
            SentinelCallNotificationHelper.cancel(context)
        }
    }

    private companion object {
        const val ACTION_URI_SCHEME = "sentinel-call-action"
        const val ACTION_URI_HOST = "call"
        val CALL_ID = Regex("^call-[0-9a-f]{32}-[1-9][0-9]{0,18}$")
    }
}
