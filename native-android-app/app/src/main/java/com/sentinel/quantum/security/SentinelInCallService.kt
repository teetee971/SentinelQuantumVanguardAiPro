package com.sentinel.quantum.security

import android.telecom.Call
import android.telecom.InCallService
import android.os.Build
import android.content.Intent
import com.sentinel.quantum.SentinelInCallActivity

/**
 * ROLE_DIALER in-call foundation. Exposes only bounded call state/actions to Sentinel UI;
 * the Telecom Call object remains owned by this service.
 */
class SentinelInCallService : InCallService() {
    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            publish(call)
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) {
            publish(call)
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        currentCall?.unregisterCallback(callback)
        currentCall = call
        call.registerCallback(callback)
        publish(call)
        // ROLE_DIALER in-call UI must be surfaced explicitly. The activity is private
        // and receives no call object; all call control remains bounded in this service.
        startActivity(
            Intent(this, SentinelInCallActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    }

    override fun onDestroy() {
        currentCall?.unregisterCallback(callback)
        currentCall = null
        snapshot = null
        super.onDestroy()
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
            displayName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                call.details.contactDisplayName?.toString()?.take(MAX_LABEL_CHARS)
            } else null,
            handle = call.details.handle?.schemeSpecificPart?.take(MAX_HANDLE_CHARS),
            canHold = call.details.hasProperty(Call.Details.PROPERTY_GENERIC_CONFERENCE).not() &&
                call.details.can(Call.Details.CAPABILITY_HOLD),
            supportsHold = call.details.can(Call.Details.CAPABILITY_SUPPORT_HOLD),
            canMergeConference = call.details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE),
            canSwapConference = call.details.can(Call.Details.CAPABILITY_SWAP_CONFERENCE)
        )
    }

    data class CallSnapshot(
        val state: Int,
        val displayName: String?,
        val handle: String?,
        val canHold: Boolean,
        val supportsHold: Boolean,
        val canMergeConference: Boolean,
        val canSwapConference: Boolean
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
            call.answer(android.telecom.VideoProfile.STATE_AUDIO_ONLY)
            true
        } ?: false

        fun reject(): Boolean = currentCall?.let { call ->
            if (call.state != Call.STATE_RINGING) return@let false
            call.reject(false, null)
            true
        } ?: false

        fun disconnect(): Boolean = currentCall?.let { call ->
            if (call.state == Call.STATE_DISCONNECTED || call.state == Call.STATE_DISCONNECTING) return@let false
            call.disconnect()
            true
        } ?: false

        fun startDtmf(digit: Char): Boolean {
            if (digit !in "0123456789*#") return false
            return currentCall?.let { call ->
                if (call.state != Call.STATE_ACTIVE) return@let false
                call.playDtmfTone(digit)
                true
            } ?: false
        }

        fun stopDtmf(): Boolean = currentCall?.let { call ->
            if (call.state != Call.STATE_ACTIVE) return@let false
            call.stopDtmfTone()
            true
        } ?: false
    }
}
