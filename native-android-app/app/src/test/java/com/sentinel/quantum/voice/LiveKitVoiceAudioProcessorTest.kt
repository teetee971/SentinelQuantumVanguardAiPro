package com.sentinel.quantum.voice

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveKitVoiceAudioProcessorTest {
    private fun floatFrame(samples: FloatArray): ByteBuffer =
        ByteBuffer.allocateDirect(samples.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .apply {
                asFloatBuffer().put(samples)
                position(0)
            }

    private fun read(buffer: ByteBuffer, count: Int): FloatArray {
        val out = FloatArray(count)
        buffer.duplicate()
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .get(out)
        return out
    }

    private fun testSamples(size: Int = 480): FloatArray =
        FloatArray(size) { index -> ((index % 80) - 40) * 700f }

    @Test fun disabledProcessorLeavesNativeFloatCaptureUntouched() {
        val processor = LiveKitVoiceAudioProcessor()
        processor.initializeAudioProcessing(48_000, 1)
        val input = testSamples()
        val buffer = floatFrame(input)

        processor.processAudio(3, 480, buffer)

        assertArrayEquals(input, read(buffer, input.size), 0f)
    }

    @Test fun enabledProcessorRewritesOutgoingNativeFloatCaptureInPlace() {
        val processor = LiveKitVoiceAudioProcessor(
            initialEffect = VoiceAddonPolicy.Effect.DEEP,
            initiallyEnabled = true
        )
        processor.initializeAudioProcessing(48_000, 1)
        val input = testSamples()

        processor.processAudio(3, 480, floatFrame(input))
        val buffer = floatFrame(input)
        processor.processAudio(3, 480, buffer)
        val output = read(buffer, input.size)

        assertFalse(input.contentEquals(output))
        assertTrue(output.any { it != 0f })
        assertTrue(output.all { it in -LiveVoiceTransformEngine.FLOAT_S16_FULL_SCALE..LiveVoiceTransformEngine.FLOAT_S16_FULL_SCALE })
    }

    @Test fun nativeFloatBufferKeepsWebRtcFloatS16Scale() {
        val processor = LiveKitVoiceAudioProcessor(
            initialEffect = VoiceAddonPolicy.Effect.DEEP,
            initiallyEnabled = true
        )
        processor.initializeAudioProcessing(48_000, 1)
        val input = testSamples()
        processor.processAudio(3, 480, floatFrame(input))
        val buffer = floatFrame(input)
        processor.processAudio(3, 480, buffer)
        val output = read(buffer, input.size)

        assertTrue(output.any { kotlin.math.abs(it) > 1_000f })
    }

    @Test fun threeBand48kCallbackProcessesTheWhole480Frame() {
        val processor = LiveKitVoiceAudioProcessor(
            initialEffect = VoiceAddonPolicy.Effect.BRIGHT,
            initiallyEnabled = true
        )
        processor.initializeAudioProcessing(48_000, 1)
        val input = testSamples()

        processor.processAudio(3, 480, floatFrame(input))
        val buffer = floatFrame(input)
        processor.processAudio(3, 480, buffer)
        val output = read(buffer, input.size)

        assertFalse(input.copyOfRange(320, 480).contentEquals(output.copyOfRange(320, 480)))
    }

    @Test fun undersizedNativeBufferIsSilencedFailClosed() {
        val processor = LiveKitVoiceAudioProcessor(
            initialEffect = VoiceAddonPolicy.Effect.DEEP,
            initiallyEnabled = true
        )
        processor.initializeAudioProcessing(48_000, 1)
        val input = testSamples(479)
        val buffer = floatFrame(input)

        processor.processAudio(3, 480, buffer)

        assertTrue(read(buffer, input.size).all { it == 0f })
    }

    @Test fun oversizedNativeBufferIsSilencedFailClosed() {
        val processor = LiveKitVoiceAudioProcessor(
            initialEffect = VoiceAddonPolicy.Effect.DEEP,
            initiallyEnabled = true
        )
        processor.initializeAudioProcessing(48_000, 1)
        val input = testSamples(481)
        val buffer = floatFrame(input)

        processor.processAudio(3, 480, buffer)

        assertTrue(read(buffer, input.size).all { it == 0f })
    }

    @Test fun invalidCallbackShapeIsSilencedWhenTransformationIsRequired() {
        val processor = LiveKitVoiceAudioProcessor(
            initialEffect = VoiceAddonPolicy.Effect.BRIGHT,
            initiallyEnabled = true
        )
        processor.initializeAudioProcessing(48_000, 1)
        val input = testSamples()
        val buffer = floatFrame(input)

        processor.processAudio(0, 480, buffer)

        assertTrue(read(buffer, input.size).all { it == 0f })
    }

    @Test fun multiChannelCaptureIsRejectedInsteadOfPartiallyTransformingVoice() {
        val processor = LiveKitVoiceAudioProcessor(
            initialEffect = VoiceAddonPolicy.Effect.DEEP,
            initiallyEnabled = true
        )

        assertThrows(IllegalArgumentException::class.java) {
            processor.initializeAudioProcessing(48_000, 2)
        }
    }

    @Test fun captureProcessorPreservesCallerBufferPositionAndOrder() {
        val processor = LiveKitVoiceAudioProcessor(
            initialEffect = VoiceAddonPolicy.Effect.DEEP,
            initiallyEnabled = true
        )
        processor.initializeAudioProcessing(48_000, 1)
        val input = testSamples()
        val buffer = ByteBuffer.allocateDirect((input.size + 2) * Float.SIZE_BYTES)
            .order(ByteOrder.BIG_ENDIAN)
            .apply {
                order(ByteOrder.nativeOrder())
                putFloat(0.1f)
                putFloat(0.2f)
                input.forEach { putFloat(it) }
                position(2 * Float.SIZE_BYTES)
                order(ByteOrder.BIG_ENDIAN)
            }

        val originalPosition = buffer.position()
        val originalOrder = buffer.order()
        processor.processAudio(3, 480, buffer)

        assertTrue(buffer.position() == originalPosition)
        assertTrue(buffer.order() == originalOrder)
    }

    @Test fun processorCanBeWiredAsLiveKitCapturePostProcessor() {
        val processor = LiveKitVoiceAudioProcessor()
        val overrides = processor.liveKitOverrides()
        assertTrue(overrides.audioOptions?.audioProcessorOptions?.capturePostProcessor === processor)
    }
}
