package com.sentinel.quantum.voice

import io.livekit.android.AudioOptions
import io.livekit.android.LiveKitOverrides
import io.livekit.android.audio.AudioProcessorInterface
import io.livekit.android.audio.AudioProcessorOptions
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * LiveKit/WebRTC capture post-processor for outgoing Sentinel call audio.
 *
 * The bundled WebRTC ExternalAudioProcessingFactory exposes a direct ByteBuffer backed by
 * a native float* from AudioBuffer::channels()[0]. AudioBuffer uses FloatS16 amplitude
 * (approximately -32768..32768), not normalized -1..1 floats. numFrames is the complete 10 ms frame
 * length; numBands describes WebRTC's internal split-band count and must not be multiplied
 * into numFrames. Treating this buffer as PCM16 corrupts the audio and is forbidden here.
 *
 * LiveKit invokes [processAudio] after capture and before WebRTC encoding/transmission.
 */
class LiveKitVoiceAudioProcessor(
    initialEffect: VoiceAddonPolicy.Effect = VoiceAddonPolicy.Effect.NATURAL,
    initiallyEnabled: Boolean = false
) : AudioProcessorInterface {
    @Volatile
    private var enabled = initiallyEnabled

    @Volatile
    private var effect = initialEffect

    private val lock = Any()
    private var sampleRateHz = 48_000
    private var channelCount = 1
    private var pipeline = SentinelVoipVoicePipeline(sampleRateHz).apply {
        configure(initiallyEnabled, initialEffect)
    }
    private var inputScratch = FloatArray(0)
    private var outputScratch = FloatArray(0)

    override fun getName(): String = "SentinelLiveVoiceTransform"

    override fun initializeAudioProcessing(sampleRateHz: Int, numChannels: Int) {
        require(sampleRateHz in 8_000..48_000) { "Unsupported LiveKit sample rate" }
        require(numChannels >= 1) { "Capture must expose at least one channel" }
        synchronized(lock) {
            this.sampleRateHz = sampleRateHz
            this.channelCount = numChannels
            pipeline = SentinelVoipVoicePipeline(sampleRateHz).apply {
                configure(enabled, effect)
            }
            inputScratch = FloatArray(0)
            outputScratch = FloatArray(0)
        }
    }

    override fun resetAudioProcessing(newRate: Int) {
        initializeAudioProcessing(newRate, channelCount)
    }

    override fun isEnabled(): Boolean = enabled

    fun configure(
        enabled: Boolean,
        effect: VoiceAddonPolicy.Effect
    ) {
        synchronized(lock) {
            this.enabled = enabled
            this.effect = effect
            pipeline.configure(enabled, effect)
        }
    }

    override fun processAudio(numBands: Int, numFrames: Int, buffer: ByteBuffer) {
        if (!enabled ||
            effect == VoiceAddonPolicy.Effect.NATURAL ||
            numBands <= 0 ||
            numFrames <= 0
        ) {
            return
        }

        synchronized(lock) {
            val availableFrames = buffer.remaining() / Float.SIZE_BYTES
            // Native WebRTC passes a direct float buffer for AudioBuffer::channels()[0].
            // If the callback shape is inconsistent, leave audio untouched rather than
            // reading past the supplied native memory on the real-time thread.
            if (availableFrames < numFrames) return

            ensureScratchCapacity(numFrames)
            val originalPosition = buffer.position()
            val originalOrder = buffer.order()
            buffer.order(ByteOrder.nativeOrder())
            try {
                for (frame in 0 until numFrames) {
                    val sampleOffsetBytes =
                        originalPosition + (frame * Float.SIZE_BYTES)
                    inputScratch[frame] = buffer.getFloat(sampleOffsetBytes)
                }

                pipeline.processOutgoingMicFrameInto(
                    input = inputScratch,
                    output = outputScratch
                )

                for (frame in 0 until numFrames) {
                    val sampleOffsetBytes =
                        originalPosition + (frame * Float.SIZE_BYTES)
                    buffer.putFloat(sampleOffsetBytes, outputScratch[frame])
                }
            } finally {
                buffer.order(originalOrder)
                buffer.position(originalPosition)
            }
        }
    }

    private fun ensureScratchCapacity(frames: Int) {
        if (inputScratch.size == frames) return
        inputScratch = FloatArray(frames)
        outputScratch = FloatArray(frames)
    }

    /**
     * capturePostProcessor runs after microphone capture and before WebRTC transmission.
     * Signaling credentials/room connection remain separate.
     */
    fun liveKitOverrides(): LiveKitOverrides =
        LiveKitOverrides(
            audioOptions = AudioOptions(
                audioProcessorOptions = AudioProcessorOptions(
                    capturePostProcessor = this
                )
            )
        )
}
