package com.sentinel.quantum.security

import android.telecom.Call
import android.telecom.InCallService
import android.os.Build

/**
 * ROLE_DIALER in-call foundation. Exposes only bounded call state/actions to Sentinel UI;
 * the Telecom Call object remains owned by this service.
 */
class SentinelInCallService : InCallService() {
    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            publish(call)
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        currentCall?.unregisterCallback(callback)
        currentCall = call
        call.registerCallback(callback)
        publish(call)
    }

    override fun onCallRemoved(call: Call) {
        call.unregisterCallback(callback)
        if (currentCall === call) {
            currentCall = null
            snapshot = null
        }
        super.onCallRemoved(call)
    }

    private fun publish(call: Call) {
        snapshot = CallSnapshot(
            state = call.state,
            displayName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {\n                call.details.contactDisplayName?.toString()?.take(MAX_LABEL_CHARS)\n            } else null,
            handle = call.details.handle?.schemeSpecificPart?.take(MAX_HANDLE_CHARS)
        )
    }

    data class CallSnapshot(
        val state: Int,
        val displayName: String?,
        val handle: String?
    )

    companion object {
        private const val MAX_LABEL_CHARS = 120
        private const val MAX_HANDLE_CHARS = 64

        @Volatile private var currentCall: Call? = null
        @Volatile private var snapshot: CallSnapshot? = null

        fun currentSnapshot(): CallSnapshot? = snapshot
        fun hasActiveCall(): Boolean = currentCall != null

        fun answer(): Boolean = currentCall?.let { call ->
            if (call.state != Call.STATE_RINGING) return@let false
            call.answer(0)
            true
        } ?: false

        fun reject(): Boolean = currentCall?.let { call ->
            if (call.state != Call.STATE_RINGING) return@let false
            call.reject(false, null)
            true
        } ?: false

        fun disconnect(): Boolean = currentCall?.let { call ->
            call.disconnect()
            true
        } ?: false

        fun playDtmf(digit: Char): Boolean {
            if (digit !in "0123456789*#") return false
            return currentCall?.let { call ->
                call.playDtmfTone(digit)
                call.stopDtmfTone()
                true
            } ?: false
        }
    }
}
