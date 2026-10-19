package com.sentinel.quantum.ui.screens

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Static guardrails; runtime UI and analyzer behavior require separate device tests. */
class EmailSecuritySourceContractTest {
    private val source = File(
        "src/main/java/com/sentinel/quantum/ui/screens/EmailSecurityScreen.kt"
    ).readText()

    @Test fun editsInvalidateOldVerdicts() {
        assertTrue(source.contains("editRevision++"))
        assertTrue(source.contains("result = null"))
        assertTrue(source.contains("revisionAtStart == editRevision"))
    }

    @Test fun errorsReleaseBusyStateAndDoNotCreateThreats() {
        assertTrue(source.contains("catch (cancelled: CancellationException)"))
        assertTrue(source.contains("finally {"))
        assertTrue(source.contains("isAnalyzing = false"))
        assertTrue(source.contains("analysisError?.let"))
        assertTrue(source.contains("result?.accepted != true -> SentinelStatus.LIMITED"))
    }
}
