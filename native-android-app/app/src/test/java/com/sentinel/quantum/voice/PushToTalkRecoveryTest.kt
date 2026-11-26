package com.sentinel.quantum.voice

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PushToTalkRecoveryTest {

    @Test
    fun closeRetriesMicrophoneDisableAfterFailedRelease() = runBlocking {
        val changes = mutableListOf<Boolean>()
        var disableAttempts = 0

        val session = PushToTalkSession { enabled ->
            changes += enabled

            if (!enabled) {
                disableAttempts++
                if (disableAttempts == 1) {
                    Result.failure<Unit>(
                        IllegalStateException("Simulated mute failure")
                    )
                } else {
                    Result.success(Unit)
                }
            } else {
                Result.success(Unit)
            }
        }

        assertTrue(session.press().isSuccess)
        assertTrue(session.release().isFailure)
        assertEquals(PushToTalkSession.State.FAILED, session.state())

        val closed = session.close()

        assertTrue("Close must retry microphone disable", closed.isSuccess)
        assertEquals(listOf(true, false, false), changes)
        assertEquals(PushToTalkSession.State.CLOSED, session.state())
    }
}
