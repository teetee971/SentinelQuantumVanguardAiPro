package com.sentinel.quantum.security

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService

/** Android system entrypoint. Decisions are local, synchronous, and user-reversible. */
class SentinelCallScreeningService : CallScreeningService() {
    override fun onScreenCall(callDetails: Call.Details) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            callDetails.callDirection != Call.Details.DIRECTION_INCOMING) return

        val snapshot = CallBlocklistStore(this).snapshot()
        val decision = CallRuleEngine(snapshot.blockedNumberHashes, snapshot.blockedPrefixes)
            .evaluate(callDetails.handle?.schemeSpecificPart)
        val response = CallResponse.Builder()
        when (decision.action) {
            CallRuleEngine.Action.BLOCK -> response
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSkipCallLog(false)
                .setSkipNotification(false)
            CallRuleEngine.Action.SILENCE -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                response.setSilenceCall(true)
            }
            CallRuleEngine.Action.ALLOW -> Unit
        }
        respondToCall(callDetails, response.build())
        LocalLogger(this).log(LocalLogger.LogLevel.SECURITY, "CallScreening",
            "Décision=${decision.action} source=${decision.source} motif=${decision.reason}")
    }
}
