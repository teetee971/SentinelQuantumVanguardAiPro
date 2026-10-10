package com.sentinel.quantum.voice

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PushToTalkSessionTest {
    @Test
    fun microphoneIsEnabledOnlyWhileTheTalkButtonIsPressed() = runBlocking {
        val changes = mutableListOf<Boolean>()
        val session = PushToTalkSession { enabled ->
            changes += enabled
            Result.success(Unit)
        }

        assertEquals(PushToTalkSession.State.READY, session.state())
        assertTrue(session.press().isSuccess)
        assertEquals(PushToTalkSession.State.TRANSMITTING, session.state())
        assertEquals(listOf(true), changes)

        assertTrue(session.release().isSuccess)
        assertEquals(PushToTalkSession.State.READY, session.state())
        assertEquals(listOf(true, false), changes)
    }

    @Test
    fun duplicatePressIsRejectedWithoutReopeningTheMicrophone() = runBlocking {
        var enableCalls = 0
        val session = PushToTalkSession {
            enableCalls += 1
            Result.success(Unit)
        }

        assertTrue(session.press().isSuccess)
        val duplicate = session.press()

        assertFalse(duplicate.isSuccess)
        assertEquals(1, enableCalls)
        assertEquals(PushToTalkSession.State.TRANSMITTING, session.state())
    }

    @Test
    fun failedMicrophoneEnableFailsClosed() = runBlocking {
        val failure = SecurityException("microphone gate refused")
        val session = PushToTalkSession { Result.failure<Unit>(failure) }

        val result = session.press()

        assertFalse(result.isSuccess)
        assertEquals(failure, result.exceptionOrNull())
        assertEquals(PushToTalkSession.State.FAILED, session.state())
    }

    @Test
    fun closeCutsMicrophoneBeforeClosingTheSession() = runBlocking {
        val changes = mutableListOf<Boolean>()
        val session = PushToTalkSession { enabled ->
            changes += enabled
            Result.success(Unit)
        }

        assertTrue(session.press().isSuccess)
        assertTrue(session.close().isSuccess)

        assertEquals(listOf(true, false), changes)
        assertEquals(PushToTalkSession.State.CLOSED, session.state())
        assertFalse(session.press().isSuccess)
    }
}
