package com.sentinel.quantum.security

/** Main-thread notification state; only a successful post consumes the incoming alert. */
internal class IncomingCallAlertPolicy {
    private var lastIncomingCallId: String? = null
    fun shouldAlert(incoming: Boolean, callId: String): Boolean =
        incoming && callId != lastIncomingCallId
    fun posted(incoming: Boolean, callId: String) {
        lastIncomingCallId = if (incoming) callId else null
    }
    fun clear() { lastIncomingCallId = null }
}
