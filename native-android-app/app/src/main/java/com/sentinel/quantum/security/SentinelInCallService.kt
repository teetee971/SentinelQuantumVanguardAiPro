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
    private var connectedEvidenceRecorded = false
    private var currentDirection = "UNKNOWN"

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            publish(call)
            if (state == Call.STATE_RINGING) {
                currentSnapshot()?.let { snapshot ->
                    if (!SentinelCallNotificationHelper.showIncoming(this@SentinelInCallService, snapshot)) {
                        showInCallActivity()
                    }
                }
            } else {
                SentinelCallNotificationHelper.cancel(this@SentinelInCallService)
            }
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) {
            publish(call)
            if (call.state == Call.STATE_RINGING) {
                currentSnapshot()?.let {
                    if (!SentinelCallNotificationHelper.showIncoming(this@SentinelInCallService, it)) {
                        showInCallActivity()
                    }
                }
            }
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        currentCall?.unregisterCallback(callback)
        currentCall = call
        connectedEvidenceRecorded = false
        currentDirection = resolveDirection(call)
        call.registerCallback(callback)
        publish(call)

        if (call.state == Call.STATE_RINGING) {
            val posted = currentSnapshot()?.let {
                SentinelCallNotificationHelper.showIncoming(this, it)
            } ?: false
            if (!posted) showInCallActivity()
        } else {
            showInCallActivity()
        }
    }

    override fun onBringToForeground(showDialpad: Boolean) {
        super.onBringToForeground(showDialpad)
        showInCallActivity()
    }

    override fun onDestroy() {
        currentCall?.unregisterCallback(callback)
        currentCall = null
        snapshot = null
        connectedEvidenceRecorded = false
        currentDirection = "UNKNOWN"
        SentinelCallNotificationHelper.cancel(this)
        super.onDestroy()
    }

    override fun onCallRemoved(call: Call) {
        call.unregisterCallback(callback)
        if (currentCall === call) {
            currentCall = null
            snapshot = null
            connectedEvidenceRecorded = false
            currentDirection = "UNKNOWN"
            SentinelCallNotificationHelper.cancel(this)
        }
        super.onCallRemoved(call)
    }

    private fun showInCallActivity() {
        startActivity(
            Intent(this, SentinelInCallActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    }

    private fun publish(call: Call) {
        if (currentDirection == "UNKNOWN") {
            currentDirection = resolveDirection(call)
        }
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

        if (
            call.state == Call.STATE_ACTIVE &&
            !connectedEvidenceRecorded &&
            currentDirection in setOf("INCOMING", "OUTGOING")
        ) {
            val stored = PhonePrivateTimelineStore(this).append(
                PhonePrivateTimeline.Event(
                    kind = PhonePrivateTimeline.Kind.CALL,
                    timestampMs = System.currentTimeMillis(),
                    direction = currentDirection,
                    signal = PhoneCorePhysicalValidation.SIGNAL_CALL_ACTIVE
                )
            )
            if (stored) connectedEvidenceRecorded = true
        }
    }

    private fun resolveDirection(call: Call): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when (call.details.callDirection) {
                Call.Details.DIRECTION_INCOMING -> "INCOMING"
                Call.Details.DIRECTION_OUTGOING -> "OUTGOING"
                else -> "UNKNOWN"
            }
        } else if (call.state == Call.STATE_RINGING) {
            "INCOMING"
        } else {
            "UNKNOWN"
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

        fun hold(): Boolean = currentCall?.let { call ->
            if (call.state != Call.STATE_ACTIVE) return@let false
            if (call.details.hasProperty(Call.Details.PROPERTY_GENERIC_CONFERENCE)) return@let false
            if (!call.details.can(Call.Details.CAPABILITY_HOLD)) return@let false
            call.hold()
            true
        } ?: false

        fun unhold(): Boolean = currentCall?.let { call ->
            if (call.state != Call.STATE_HOLDING) return@let false
            if (!call.details.can(Call.Details.CAPABILITY_SUPPORT_HOLD)) return@let false
            call.unhold()
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
