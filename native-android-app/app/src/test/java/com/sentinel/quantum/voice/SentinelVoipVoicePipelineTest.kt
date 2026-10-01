package com.sentinel.quantum.voice

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SentinelVoipVoicePipelineTest {
    private val frame = ShortArray(320) { index -> (((index % 64) - 32) * 600).toShort() }

    @Test fun disabledPipelineLeavesOutgoingMicrophoneFrameUnchanged() {
        val pipeline = SentinelVoipVoicePipeline(sampleRateHz = 16_000)
        val output = pipeline.processOutgoingMicFrame(frame)
        assertArrayEquals(frame, output)
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

    @Test fun newCallResetClearsPerCallProcessingState() {
        val pipeline = SentinelVoipVoicePipeline(sampleRateHz = 16_000)
        pipeline.configure(enabled = true, effect = VoiceAddonPolicy.Effect.BRIGHT)
        pipeline.processOutgoingMicFrame(frame)
        pipeline.resetForNewCall()
        assertEquals(0L, pipeline.processedFrameCount())
    }
}
