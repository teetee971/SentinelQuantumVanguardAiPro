package com.sentinel.quantum

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class InCallDialpadVisibilityContractTest {
    @Test
    fun activeDialpadTakesVisualPriorityBeforeCallerDetails() {
        val source = File(
            locateSourceRoot(),
            "com/sentinel/quantum/SentinelInCallActivity.kt"
        ).readText()

        val focusedDialpadBranch = source.indexOf(
            "if (showDialpad && snapshot.state == Call.STATE_ACTIVE) {"
        )
        val callerHero = source.indexOf("CallerHero(")

        assertTrue(
            "Active dialpad must have an explicit focused-content branch",
            focusedDialpadBranch >= 0
        )
        assertTrue(
            "Focused dialpad must be selected before caller details so it cannot open below the fold",
            focusedDialpadBranch < callerHero
        )
        assertTrue(
            "Focused branch must render the real DTMF panel",
            source.substring(focusedDialpadBranch, callerHero).contains("DialpadPanel(snapshot)")
        )
    }

    private fun locateSourceRoot(): File {
        val candidates = listOf(
            File("src/main/java"),
            File("app/src/main/java"),
            File("native-android-app/app/src/main/java")
        )
        return candidates.firstOrNull { root ->
            File(root, "com/sentinel/quantum/SentinelInCallActivity.kt").isFile
        } ?: error("Unable to locate Android main source root from ${File(".").absolutePath}")
    }
}
