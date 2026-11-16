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

        // Call.answer()/reject() only submit a request to Telecom; they do not synchronously prove
        // that the call changed state. Never cancel the ongoing incoming-call notification here.
        // SentinelInCallService owns cancellation after it observes a real transition away from
        // STATE_RINGING. If Telecom ignores a stale/racing request, the user must keep the call UI.
        when {
            intent.action == SentinelCallNotificationHelper.ACTION_ANSWER && kind == "answer" ->
                SentinelInCallService.answer(callId)
            intent.action == SentinelCallNotificationHelper.ACTION_REJECT && kind == "reject" ->
                SentinelInCallService.reject(callId)
        }
    }

    private companion object {
        const val ACTION_URI_SCHEME = "sentinel-call-action"
        const val ACTION_URI_HOST = "call"
        val CALL_ID = Regex("^call-[0-9a-f]{32}-[1-9][0-9]{0,18}$")
    }
}
