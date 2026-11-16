package com.sentinel.quantum

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceStudioRoomIsolationContractTest {
    @Test
    fun diagnostic_voice_room_is_derived_from_the_verified_device() {
        val source = File(
            "src/main/java/com/sentinel/quantum/VoiceStudioActivity.kt"
        ).readText()

        assertTrue(source.contains("roomId = demoRoomId(deviceId)"))
        assertTrue(source.contains("private fun demoRoomId(deviceId: String): String"))
        assertFalse(source.contains("const val DEMO_ROOM_ID = \"demo-room\""))
    }
}
