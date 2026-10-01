package com.sentinel.quantum.voice

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SentinelVoipCallSessionTest {
    private class RecordingTransport : SentinelVoipCallSession.OutgoingAudioTransport {
        val frames = mutableListOf<ShortArray>()
        override fun sendOutgoingPcm16(pcm16Mono: ShortArray) {
            frames += pcm16Mono.copyOf()
        }
    }

    private fun frame() = ShortArray(320) { index -> (((index % 64) - 32) * 600).toShort() }

    @Test fun activeCallRoutesNaturalMicrophoneAudioThroughOwnedTransport() {
        val transport = RecordingTransport()
        val session = SentinelVoipCallSession(16_000, transport)
        val input = frame()

        session.start(false, VoiceAddonPolicy.Effect.NATURAL)
        assertTrue(session.submitMicrophoneFrame(input))

        assertArrayEquals(input, transport.frames.single())
    }

    @Test fun enabledLiveEffectTransformsBeforeTransportSend() {
        val transport = RecordingTransport()
        val session = SentinelVoipCallSession(16_000, transport)
        val input = frame()

        session.start(true, VoiceAddonPolicy.Effect.DEEP)
        assertTrue(session.submitMicrophoneFrame(input))
        assertTrue(session.submitMicrophoneFrame(input))

        assertFalse(input.contentEquals(transport.frames.last()))
    }

    @Test fun inactiveOrEndedCallCannotEmitMicrophoneFrames() {
        val transport = RecordingTransport()
        val session = SentinelVoipCallSession(16_000, transport)

        assertFalse(session.submitMicrophoneFrame(frame()))
        session.start(true, VoiceAddonPolicy.Effect.BRIGHT)
        session.end()
        assertFalse(session.submitMicrophoneFrame(frame()))
        assertTrue(transport.frames.isEmpty())
    }
}
