package com.sentinel.quantum.voice

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveKitVoiceAudioProcessorTest {
    private fun pcmFrame(samples: ShortArray): ByteBuffer =
        ByteBuffer.allocateDirect(samples.size * Short.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                asShortBuffer().put(samples)
                position(0)
            }

    private fun read(buffer: ByteBuffer, count: Int): ShortArray {
        val out = ShortArray(count)
        buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(out)
        return out
    }

    @Test fun disabledProcessorLeavesLiveKitCaptureUntouched() {
        val processor = LiveKitVoiceAudioProcessor()
        processor.initializeAudioProcessing(48_000, 1)
        val input = ShortArray(480) { index -> (((index % 80) - 40) * 600).toShort() }
        val buffer = pcmFrame(input)

        processor.processAudio(1, 480, buffer)

        assertArrayEquals(input, read(buffer, input.size))
    }

    @Test fun enabledProcessorRewritesOutgoingLiveKitCaptureInPlace() {
        val processor = LiveKitVoiceAudioProcessor(
            initialEffect = VoiceAddonPolicy.Effect.DEEP,
            initiallyEnabled = true
        )
        processor.initializeAudioProcessing(48_000, 1)
        val input = ShortArray(480) { index -> (((index % 80) - 40) * 600).toShort() }

        // First frame warms the bounded delay line.
        processor.processAudio(1, 480, pcmFrame(input))
        val buffer = pcmFrame(input)
        processor.processAudio(1, 480, buffer)
        val output = read(buffer, input.size)

        assertFalse(input.contentEquals(output))
        assertTrue(output.any { it.toInt() != 0 })
    }

    @Test fun stereoCaptureIsTransformedPerChannelWithoutChangingFrameShape() {
        val processor = LiveKitVoiceAudioProcessor(
            initialEffect = VoiceAddonPolicy.Effect.BRIGHT,
            initiallyEnabled = true
        )
        processor.initializeAudioProcessing(48_000, 2)
        val input = ShortArray(960) { index ->
            val frame = index / 2
            if (index % 2 == 0) {
                (((frame % 80) - 40) * 500).toShort()
            } else {
                ((40 - (frame % 80)) * 400).toShort()
            }
        }

        processor.processAudio(1, 480, pcmFrame(input))
        val buffer = pcmFrame(input)
        processor.processAudio(1, 480, buffer)
        val output = read(buffer, input.size)

        assertFalse(input.contentEquals(output))
        assertTrue(output.filterIndexed { index, _ -> index % 2 == 0 }.any { it.toInt() != 0 })
        assertTrue(output.filterIndexed { index, _ -> index % 2 == 1 }.any { it.toInt() != 0 })
    }

    @Test fun captureProcessorPreservesCallerBufferPosition() {
        val processor = LiveKitVoiceAudioProcessor(
            initialEffect = VoiceAddonPolicy.Effect.DEEP,
            initiallyEnabled = true
        )
        processor.initializeAudioProcessing(48_000, 1)
        val input = ShortArray(480) { index -> (((index % 80) - 40) * 500).toShort() }
        val buffer = ByteBuffer.allocateDirect((input.size + 2) * Short.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                putShort(111)
                putShort(222)
                input.forEach { putShort(it) }
                position(2 * Short.SIZE_BYTES)
            }

        val originalPosition = buffer.position()
        processor.processAudio(1, 480, buffer)

        assertTrue(buffer.position() == originalPosition)
    }

    @Test fun processorCanBeWiredAsLiveKitCapturePostProcessor() {
        val processor = LiveKitVoiceAudioProcessor()
        val overrides = processor.liveKitOverrides()
        assertTrue(overrides.audioOptions?.audioProcessorOptions?.capturePostProcessor === processor)
    }
}
