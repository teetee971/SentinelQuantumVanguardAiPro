package com.sentinel.quantum.security

import android.telecom.Call
import android.telecom.InCallService

/** Passive ROLE_DIALER in-call foundation; call controls are added only with a real in-call UI. */
class SentinelInCallService : InCallService() {
    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        currentCall = call
    }

    override fun onCallRemoved(call: Call) {
        if (currentCall === call) currentCall = null
        super.onCallRemoved(call)
    }

    companion object {
        @Volatile
        private var currentCall: Call? = null

        fun hasActiveCall(): Boolean = currentCall != null
    }
}
