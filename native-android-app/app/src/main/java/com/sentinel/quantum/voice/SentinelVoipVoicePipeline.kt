package com.sentinel.quantum.voice

/**
 * Outgoing microphone processing stage for a Sentinel-owned VoIP call.
 *
 * A future WebRTC/SIP media transport must pass each outgoing PCM16 microphone frame
 * through [processOutgoingMicFrame] before encoding/packetization. This class intentionally
 * has no carrier/SIM integration: Android does not expose that media path to an ordinary
 * third-party dialer.
 */
class SentinelVoipVoicePipeline(sampleRateHz: Int) {
    data class Configuration(
        val enabled: Boolean,
        val effect: VoiceAddonPolicy.Effect
    )

    private val lock = Any()
    private val transformer = LiveVoiceTransformEngine(sampleRateHz)
    private var configuration = Configuration(
        enabled = false,
        effect = VoiceAddonPolicy.Effect.NATURAL
    )
    private var processedFrames = 0L

    fun configure(enabled: Boolean, effect: VoiceAddonPolicy.Effect) {
        synchronized(lock) {
            if (configuration.effect != effect || configuration.enabled != enabled) {
                transformer.reset()
            }
            configuration = Configuration(enabled = enabled, effect = effect)
        }
    }

    fun currentConfiguration(): Configuration = synchronized(lock) { configuration }

    fun processedFrameCount(): Long = synchronized(lock) { processedFrames }

    fun resetForNewCall() {
        synchronized(lock) {
            transformer.reset()
            processedFrames = 0L
        }
    }

    fun processOutgoingMicFrame(pcm16Mono: ShortArray): ShortArray =
        synchronized(lock) {
            processedFrames++
            if (!configuration.enabled) {
                pcm16Mono.copyOf()
            } else {
                transformer.processPcm16(pcm16Mono, configuration.effect)
            }
        }
}
