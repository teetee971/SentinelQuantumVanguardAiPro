package com.sentinel.quantum.voice

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveVoiceTransformEngineTest {
    private fun testFrame(size: Int = 960): FloatArray =
        FloatArray(size) { index -> ((index % 80) - 40) * 700f }

    @Test fun naturalEffectIsBitExactAndKeepsFrameLength() {
        val engine = LiveVoiceTransformEngine(sampleRateHz = 48_000)
        val input = testFrame()
        val output = engine.processFloat32(input, VoiceAddonPolicy.Effect.NATURAL)
        assertEquals(input.size, output.size)
        assertArrayEquals(input, output, 0f)
    }

    @Test fun liveEffectsPreserveFrameLengthRangeAndChangeAudio() {
        val deep = LiveVoiceTransformEngine(sampleRateHz = 48_000)
        val bright = LiveVoiceTransformEngine(sampleRateHz = 48_000)
        val input = testFrame()

        deep.processFloat32(input, VoiceAddonPolicy.Effect.DEEP)
        bright.processFloat32(input, VoiceAddonPolicy.Effect.BRIGHT)
        val deepOutput = deep.processFloat32(input, VoiceAddonPolicy.Effect.DEEP)
        val brightOutput = bright.processFloat32(input, VoiceAddonPolicy.Effect.BRIGHT)

        assertEquals(input.size, deepOutput.size)
        assertEquals(input.size, brightOutput.size)
        assertFalse(input.contentEquals(deepOutput))
        assertFalse(input.contentEquals(brightOutput))
        assertTrue(deepOutput.any { it != 0f })
        assertTrue(brightOutput.any { it != 0f })
        assertTrue(deepOutput.all { it in -LiveVoiceTransformEngine.FLOAT_S16_FULL_SCALE..LiveVoiceTransformEngine.FLOAT_S16_FULL_SCALE })
        assertTrue(brightOutput.all { it in -LiveVoiceTransformEngine.FLOAT_S16_FULL_SCALE..LiveVoiceTransformEngine.FLOAT_S16_FULL_SCALE })
    }

    @Test fun preservesWebRtcFloatS16AmplitudeInsteadOfNormalizingToOne() {
        val engine = LiveVoiceTransformEngine(sampleRateHz = 48_000)
        val input = testFrame(480)
        engine.processFloat32(input, VoiceAddonPolicy.Effect.DEEP)
        val output = engine.processFloat32(input, VoiceAddonPolicy.Effect.DEEP)

        assertTrue(output.any { kotlin.math.abs(it) > 1_000f })
        assertTrue(output.all { kotlin.math.abs(it) <= LiveVoiceTransformEngine.FLOAT_S16_FULL_SCALE })
    }

    @Test fun resetMakesProcessingDeterministicAcrossCalls() {
        val engine = LiveVoiceTransformEngine(sampleRateHz = 16_000)
        val input = testFrame(320)

        val first = engine.processFloat32(input, VoiceAddonPolicy.Effect.DEEP)
        engine.processFloat32(input, VoiceAddonPolicy.Effect.DEEP)
        engine.reset()
        val afterReset = engine.processFloat32(input, VoiceAddonPolicy.Effect.DEEP)

        assertArrayEquals(first, afterReset, 0f)
    }
}
