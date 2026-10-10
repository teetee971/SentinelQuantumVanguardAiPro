package com.sentinel.quantum.talkiewalkie

data class GuardSnapshot(
    val nowMs: Long,
    val sessionId: String,
    val floorLease: FloorLease?,
    val channelPolicy: ChannelPolicy,
    val microphonePermissionGranted: Boolean,
    val transportConnected: Boolean,
    val audioFocusGranted: Boolean,
    val state: TalkieWalkieState
)

enum class GuardDenialReason {
    INVALID_STATE,
    CHANNEL_EXPIRED,
    TALK_NOT_ALLOWED,
    MICROPHONE_PERMISSION_MISSING,
    TRANSPORT_DISCONNECTED,
    AUDIO_FOCUS_MISSING,
    INVALID_FLOOR
}

data class GuardDecision(
    val allowed: Boolean,
    val reason: GuardDenialReason?
) {
    init {
        require(allowed == (reason == null)) {
            "Allowed decisions cannot have denial reasons and denied decisions require one"
        }
    }

    companion object {
        val ALLOW = GuardDecision(allowed = true, reason = null)

        fun deny(reason: GuardDenialReason) = GuardDecision(
            allowed = false,
            reason = reason
        )
    }
}

class PushToTalkGuard {
    fun canTransmit(snapshot: GuardSnapshot): GuardDecision {
        if (snapshot.state != TalkieWalkieState.REQUESTING_FLOOR) {
            return GuardDecision.deny(GuardDenialReason.INVALID_STATE)
        }
        if (snapshot.channelPolicy.isExpired(snapshot.nowMs)) {
            return GuardDecision.deny(GuardDenialReason.CHANNEL_EXPIRED)
        }
        if (!snapshot.channelPolicy.canTalk) {
            return GuardDecision.deny(GuardDenialReason.TALK_NOT_ALLOWED)
        }
        if (!snapshot.microphonePermissionGranted) {
            return GuardDecision.deny(GuardDenialReason.MICROPHONE_PERMISSION_MISSING)
        }
        if (!snapshot.transportConnected) {
            return GuardDecision.deny(GuardDenialReason.TRANSPORT_DISCONNECTED)
        }
        if (!snapshot.audioFocusGranted) {
            return GuardDecision.deny(GuardDenialReason.AUDIO_FOCUS_MISSING)
        }
        if (snapshot.floorLease?.isValidFor(snapshot.sessionId, snapshot.nowMs) != true) {
            return GuardDecision.deny(GuardDenialReason.INVALID_FLOOR)
        }
        return GuardDecision.ALLOW
    }
}
