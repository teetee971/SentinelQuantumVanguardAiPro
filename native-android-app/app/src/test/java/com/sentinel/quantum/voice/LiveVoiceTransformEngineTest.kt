package com.sentinel.quantum.voice

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveVoiceTransformEngineTest {
    private fun testFrame(size: Int = 960): ShortArray =
        ShortArray(size) { index -> (((index % 80) - 40) * 700).toShort() }

    @Test fun naturalEffectIsBitExactAndKeepsFrameLength() {
        val engine = LiveVoiceTransformEngine(sampleRateHz = 48_000)
        val input = testFrame()
        val output = engine.processPcm16(input, VoiceAddonPolicy.Effect.NATURAL)
        assertEquals(input.size, output.size)
        assertArrayEquals(input, output)
    }

    @Test fun liveEffectsPreserveFrameLengthAndChangeAudio() {
        val deep = LiveVoiceTransformEngine(sampleRateHz = 48_000)
        val bright = LiveVoiceTransformEngine(sampleRateHz = 48_000)
        val input = testFrame()

        // Warm the bounded delay line, then inspect the next frame.
        deep.processPcm16(input, VoiceAddonPolicy.Effect.DEEP)
        bright.processPcm16(input, VoiceAddonPolicy.Effect.BRIGHT)
        val deepOutput = deep.processPcm16(input, VoiceAddonPolicy.Effect.DEEP)
        val brightOutput = bright.processPcm16(input, VoiceAddonPolicy.Effect.BRIGHT)

        assertEquals(input.size, deepOutput.size)
        assertEquals(input.size, brightOutput.size)
        assertFalse(input.contentEquals(deepOutput))
        assertFalse(input.contentEquals(brightOutput))
        assertTrue(deepOutput.any { it.toInt() != 0 })
        assertTrue(brightOutput.any { it.toInt() != 0 })
    }

    @Test fun resetMakesProcessingDeterministicAcrossCalls() {
        val engine = LiveVoiceTransformEngine(sampleRateHz = 16_000)
        val input = testFrame(320)

        val first = engine.processPcm16(input, VoiceAddonPolicy.Effect.DEEP)
        engine.processPcm16(input, VoiceAddonPolicy.Effect.DEEP)
        engine.reset()
        val afterReset = engine.processPcm16(input, VoiceAddonPolicy.Effect.DEEP)

        assertArrayEquals(first, afterReset)
    }
}
