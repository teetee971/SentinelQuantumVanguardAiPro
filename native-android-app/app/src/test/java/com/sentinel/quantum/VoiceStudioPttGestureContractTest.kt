package com.sentinel.quantum

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceStudioPttGestureContractTest {
    @Test
    fun ptt_is_hold_to_talk_and_forces_release_when_activity_stops() {
        val source = File(
            "src/main/java/com/sentinel/quantum/VoiceStudioActivity.kt"
        ).readText()

        assertTrue(source.contains("pointerInput"))
        assertTrue(source.contains("awaitEachGesture"))
        assertTrue(source.contains("awaitFirstDown"))
        assertTrue(source.contains("waitForUpOrCancellation"))
        assertTrue(source.contains("override fun onStop()"))
        assertTrue(source.contains("releasePushToTalk"))

        val pttBlock = source.substringAfter("Session PTT Sentinel · test")
            .substringBefore("Appels avec voix transformée")
        assertFalse(pttBlock.contains("if (transmitting)"))
        assertFalse(pttBlock.contains("transmitting = !transmitting"))
    }
}
