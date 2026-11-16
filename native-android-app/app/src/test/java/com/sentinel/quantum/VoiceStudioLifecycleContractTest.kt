package com.sentinel.quantum

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceStudioLifecycleContractTest {
    @Test
    fun activity_destruction_delegates_voice_teardown_to_process_scope() {
        val activitySource = File(
            "src/main/java/com/sentinel/quantum/VoiceStudioActivity.kt"
        ).readText()
        val applicationSource = File(
            "src/main/java/com/sentinel/quantum/SentinelApplication.kt"
        ).readText()

        val onDestroy = activitySource.substringAfter("override fun onDestroy()")
            .substringBefore("private companion object")

        assertTrue(onDestroy.contains("launchVoiceTeardown"))
        assertFalse(onDestroy.contains("lifecycleScope.launch"))
        assertTrue(applicationSource.contains("voiceTeardownScope"))
        assertTrue(applicationSource.contains("SupervisorJob()"))
        assertTrue(applicationSource.contains("fun launchVoiceTeardown"))
    }
}
