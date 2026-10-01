package com.sentinel.quantum.voice

/**
 * Sentinel-controlled VoIP call media session.
 *
 * This is the call-level wiring contract: microphone PCM enters the session, passes through
 * [SentinelVoipVoicePipeline], then reaches the owned media transport. A concrete WebRTC/SIP
 * transport must implement [OutgoingAudioTransport] before production calling can be enabled.
 */
class SentinelVoipCallSession(
    sampleRateHz: Int,
    private val transport: OutgoingAudioTransport
) {
    fun interface OutgoingAudioTransport {
        fun sendOutgoingPcm16(pcm16Mono: ShortArray)
    }

    enum class State { IDLE, ACTIVE, ENDED }

    private val lock = Any()
    private val voicePipeline = SentinelVoipVoicePipeline(sampleRateHz)
    private var state = State.IDLE

    fun start(
        transformEnabled: Boolean,
        effect: VoiceAddonPolicy.Effect
    ) {
        synchronized(lock) {
            voicePipeline.resetForNewCall()
            voicePipeline.configure(transformEnabled, effect)
            state = State.ACTIVE
        }
    }

    /**
     * Returns false instead of emitting media when no active Sentinel-owned call exists.
     */
    fun submitMicrophoneFrame(pcm16Mono: ShortArray): Boolean =
        synchronized(lock) {
            if (state != State.ACTIVE) return false
            val outgoing = voicePipeline.processOutgoingMicFrame(pcm16Mono)
            transport.sendOutgoingPcm16(outgoing)
            true
        }

    fun updateTransform(
        enabled: Boolean,
        effect: VoiceAddonPolicy.Effect
    ): Boolean = synchronized(lock) {
        if (state != State.ACTIVE) return false
        voicePipeline.configure(enabled, effect)
        true
    }

    fun end() {
        synchronized(lock) {
            state = State.ENDED
            voicePipeline.resetForNewCall()
        }
    }

    fun currentState(): State = synchronized(lock) { state }
}
