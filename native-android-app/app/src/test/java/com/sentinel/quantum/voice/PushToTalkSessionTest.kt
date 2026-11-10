package com.sentinel.quantum.voice

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
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

    @Test
    fun releasingDuringPressClosesTheMicrophone() = runBlocking {
        val changes = mutableListOf<Boolean>()
        val opened = CompletableDeferred<Unit>()
        val session = PushToTalkSession { enabled ->
            changes += enabled
            if (enabled) {
                opened.complete(Unit)
                awaitCancellation()
            }
            Result.success(Unit)
        }

        val job = launch { session.press() }
        opened.await()
        job.cancelAndJoin()

        assertEquals(listOf(true, false), changes)
        assertEquals(PushToTalkSession.State.READY, session.state())
    }

    @Test
    fun roomPublicationLossLeavesTheButtonReadyWithoutSending() = runBlocking {
        var enables = 0
        val session = PushToTalkSession {
            enables += 1
            Result.success(Unit)
        }
        assertTrue(session.press().isSuccess)
        session.onRoomPublicationLost()
        assertEquals(PushToTalkSession.State.READY, session.state())
        assertEquals(1, enables)
    }

    @Test
    fun roomGoneRejectsTheNextPress() = runBlocking {
        val session = PushToTalkSession { Result.success(Unit) }
        assertTrue(session.press().isSuccess)
        session.onRoomGone()
        assertEquals(PushToTalkSession.State.FAILED, session.state())
        assertFalse(session.press().isSuccess)
    }
}
