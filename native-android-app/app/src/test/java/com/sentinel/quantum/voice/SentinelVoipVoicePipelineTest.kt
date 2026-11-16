package com.sentinel.quantum.voice

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SentinelVoipVoicePipelineTest {
    private val frame = FloatArray(320) { index -> ((index % 64) - 32) / 40f }

    @Test fun disabledPipelineLeavesOutgoingMicrophoneFrameUnchanged() {
        val pipeline = SentinelVoipVoicePipeline(sampleRateHz = 16_000)
        val output = pipeline.processOutgoingMicFrame(frame)
        assertArrayEquals(frame, output, 0f)
        assertEquals(1L, pipeline.processedFrameCount())
    }

    @Test fun enabledDeepEffectTransformsBeforeTransportBoundary() {
        val pipeline = SentinelVoipVoicePipeline(sampleRateHz = 16_000)
        pipeline.configure(enabled = true, effect = VoiceAddonPolicy.Effect.DEEP)

        pipeline.processOutgoingMicFrame(frame)
        val output = pipeline.processOutgoingMicFrame(frame)

        assertEquals(frame.size, output.size)
        assertFalse(frame.contentEquals(output))
    }

    @Test fun allocationBoundedPathWritesIntoCallerOwnedFloatBuffer() {
        val pipeline = SentinelVoipVoicePipeline(sampleRateHz = 16_000)
        pipeline.configure(enabled = true, effect = VoiceAddonPolicy.Effect.DEEP)
        val output = FloatArray(frame.size)

        pipeline.processOutgoingMicFrameInto(frame, output)
        pipeline.processOutgoingMicFrameInto(frame, output)

        assertEquals(frame.size, output.size)
        assertFalse(frame.contentEquals(output))
    }

    @Test fun resetClearsPerCallProcessingState() {
        val pipeline = SentinelVoipVoicePipeline(sampleRateHz = 16_000)
        pipeline.configure(enabled = true, effect = VoiceAddonPolicy.Effect.BRIGHT)
        pipeline.processOutgoingMicFrame(frame)
        pipeline.reset()
        assertEquals(0L, pipeline.processedFrameCount())
    }
}
