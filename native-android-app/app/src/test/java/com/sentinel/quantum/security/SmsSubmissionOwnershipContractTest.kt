package com.sentinel.quantum.security

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsSubmissionOwnershipContractTest {
    @Test fun composerSubmissionGateSurvivesActivityRecreation() {
        val source = sourceFile("SmsComposeActivity.kt").readText()

        assertFalse(
            "Activity composition must not own the submission gate",
            source.contains("remember { SmsSubmissionGate() }")
        )
        assertTrue(
            "Composer must obtain a configuration-stable submission owner",
            source.contains("SmsSubmissionViewModel")
        )
    }

    private fun sourceFile(name: String): File {
        val candidates = listOf(
            File("src/main/java/com/sentinel/quantum/$name"),
            File("app/src/main/java/com/sentinel/quantum/$name"),
            File("native-android-app/app/src/main/java/com/sentinel/quantum/$name")
        )
        return candidates.firstOrNull(File::isFile)
            ?: error("Unable to locate $name from ${File(".").absolutePath}")
    }
}
