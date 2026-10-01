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
 * LiveKit invokes [processAudio] on 10 ms microphone frames before they are sent to the room.
 * The processor rewrites the PCM16 buffer in place, so the transformed signal is what the
 * WebRTC transport encodes and transmits.
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
    private var pipelines = arrayOf(SentinelVoipVoicePipeline(sampleRateHz).apply {
        configure(initiallyEnabled, initialEffect)
    })
    private var inputScratch = arrayOf(ShortArray(0))
    private var outputScratch = arrayOf(ShortArray(0))

    override fun getName(): String = "SentinelLiveVoiceTransform"

    override fun initializeAudioProcessing(sampleRateHz: Int, numChannels: Int) {
        require(sampleRateHz in 8_000..48_000) { "Unsupported LiveKit sample rate" }
        require(numChannels in 1..2) { "Only mono/stereo capture is supported" }
        synchronized(lock) {
            this.sampleRateHz = sampleRateHz
            this.channelCount = numChannels
            pipelines = Array(numChannels) {
                SentinelVoipVoicePipeline(sampleRateHz).apply {
                    configure(enabled, effect)
                }
            }
            inputScratch = Array(numChannels) { ShortArray(0) }
            outputScratch = Array(numChannels) { ShortArray(0) }
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
            pipelines.forEach { it.configure(enabled, effect) }
        }
    }

    override fun processAudio(numBands: Int, numFrames: Int, buffer: ByteBuffer) {
        if (!enabled || effect == VoiceAddonPolicy.Effect.NATURAL || numFrames <= 0) return

        synchronized(lock) {
            val availableSamples = buffer.remaining() / Short.SIZE_BYTES
            val frames = minOf(numFrames, availableSamples / channelCount)
            if (frames <= 0) return

            ensureScratchCapacity(frames)
            val originalPosition = buffer.position()
            val originalOrder = buffer.order()
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            try {
                for (frame in 0 until frames) {
                    for (channel in 0 until channelCount) {
                        val sampleOffsetBytes =
                            originalPosition +
                                ((frame * channelCount + channel) * Short.SIZE_BYTES)
                        inputScratch[channel][frame] = buffer.getShort(sampleOffsetBytes)
                    }
                }

                for (channel in 0 until channelCount) {
                    pipelines[channel].processOutgoingMicFrameInto(
                        input = inputScratch[channel],
                        output = outputScratch[channel]
                    )
                }

                for (frame in 0 until frames) {
                    for (channel in 0 until channelCount) {
                        val sampleOffsetBytes =
                            originalPosition +
                                ((frame * channelCount + channel) * Short.SIZE_BYTES)
                        buffer.putShort(sampleOffsetBytes, outputScratch[channel][frame])
                    }
                }
            } finally {
                buffer.order(originalOrder)
                buffer.position(originalPosition)
            }
        }
    }

    private fun ensureScratchCapacity(frames: Int) {
        if (inputScratch.firstOrNull()?.size == frames) return
        inputScratch = Array(channelCount) { ShortArray(frames) }
        outputScratch = Array(channelCount) { ShortArray(frames) }
    }

    /**
     * Exact LiveKit room override: capturePostProcessor runs on microphone audio before
     * WebRTC transmission. Signaling credentials/room connection remain separate.
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
