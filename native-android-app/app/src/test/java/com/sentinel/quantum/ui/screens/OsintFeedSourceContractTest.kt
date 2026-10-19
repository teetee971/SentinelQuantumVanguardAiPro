package com.sentinel.quantum.ui.screens

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OsintFeedSourceContractTest {
    private val source = File(
        "src/main/java/com/sentinel/quantum/ui/screens/OsintFeedScreen.kt"
    ).readText()

    @Test
    fun feedServiceErrorIsNotPresentedAsCriticalDeviceThreat() {
        assertTrue(
            "OSINT retrieval failure must be LIMITED, not a CRITICAL security threat",
            source.contains("errorMessage != null -> SentinelStatus.LIMITED")
        )
        assertFalse(
            "A feed outage cannot imply device compromise",
            source.contains("errorMessage != null -> SentinelStatus.BAD")
        )
    }

    @Test
    fun loadFeedsDoesNotKeepRedundantUnitExpression() {
        val redundantUnit = Regex("""\n\s*Unit\n\s*}\n\n\s*LaunchedEffect\(Unit\)""")
        assertFalse(
            "loadFeeds must not end with a redundant Unit expression before LaunchedEffect",
            redundantUnit.containsMatchIn(source)
        )
    }
}
