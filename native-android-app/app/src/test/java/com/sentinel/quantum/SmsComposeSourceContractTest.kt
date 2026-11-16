package com.sentinel.quantum

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsComposeSourceContractTest {
    private val source = File(
        "src/main/java/com/sentinel/quantum/SmsComposeActivity.kt"
    ).readText()

    @Test
    fun replyVisibilityOwnsThreadNullabilityWithoutRedundantGuard() {
        assertTrue(source.contains("val replyVisible = !showComposer && threadId != null"))
        assertFalse(source.contains("if (replyVisible && threadId != null)"))
        assertTrue(source.contains("if (replyVisible) {"))
    }
}
