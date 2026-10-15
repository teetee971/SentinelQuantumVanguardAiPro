package com.sentinel.quantum.ui.screens

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class OsintFeedSourceContractTest {
    private val source = File(
        "src/main/java/com/sentinel/quantum/ui/screens/OsintFeedScreen.kt"
    ).readText()

    @Test
    fun loadFeedsDoesNotKeepRedundantUnitExpression() {
        val redundantUnit = Regex("""\n\s*Unit\n\s*}\n\n\s*LaunchedEffect\(Unit\)""")
        assertFalse(
            "loadFeeds must not end with a redundant Unit expression before LaunchedEffect",
            redundantUnit.containsMatchIn(source)
        )
    }
}
