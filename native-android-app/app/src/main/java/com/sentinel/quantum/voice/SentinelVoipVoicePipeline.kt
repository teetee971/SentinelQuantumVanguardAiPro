package com.sentinel.quantum.voice

/**
 * Allocation-bounded outgoing microphone transform used by the concrete LiveKit/WebRTC
 * capture processor. LiveKit's external APM bridge exposes normalized Float32 samples;
 * carrier/SIM media never enters this pipeline.
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

    fun reset() {
        synchronized(lock) {
            transformer.reset()
            processedFrames = 0L
        }
    }

    fun processOutgoingMicFrame(float32Mono: FloatArray): FloatArray =
        FloatArray(float32Mono.size).also { output ->
            processOutgoingMicFrameInto(float32Mono, output)
        }

    /**
     * Reuses caller-owned Float32 buffers for the 10 ms LiveKit capture callback.
     */
    fun processOutgoingMicFrameInto(
        input: FloatArray,
        output: FloatArray
    ) {
        require(output.size >= input.size) { "Output buffer too small" }
        synchronized(lock) {
            processedFrames++
            if (!configuration.enabled || configuration.effect == VoiceAddonPolicy.Effect.NATURAL) {
                input.copyInto(output, endIndex = input.size)
            } else {
                transformer.processFloat32Into(
                    input = input,
                    output = output,
                    effect = configuration.effect
                )
            }
        }
    }
}
