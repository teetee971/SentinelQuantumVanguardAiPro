package com.sentinel.quantum.talkiewalkie

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PttTriggerInstrumentationTest {
    @Test
    fun disconnectedTriggerRoutesToTriggerLossWithoutTransportOwnership() = runBlocking {
        val target = FakeTarget()
        val trigger = PttTrigger(target)

        trigger.dispatch(PttTriggerEvent.DISCONNECTED)

        assertEquals(0, target.pressCount)
        assertEquals(0, target.releaseCount)
        assertEquals(1, target.triggerLostCount)
        assertFalse(
            PttTrigger::class.java.declaredFields.any { field ->
                TalkieWalkieTransport::class.java.isAssignableFrom(field.type)
            }
        )
    }

    private class FakeTarget : PttTriggerTarget {
        var pressCount = 0
        var releaseCount = 0
        var triggerLostCount = 0

        override suspend fun onPress() {
            pressCount += 1
        }

        override suspend fun onRelease() {
            releaseCount += 1
        }

        override suspend fun onTriggerLost() {
            triggerLostCount += 1
        }
    }
}
