package com.sentinel.quantum.ui.screens

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AboutScreenSourceContractTest {
    private val source = File(
        "src/main/java/com/sentinel/quantum/ui/screens/AboutScreen.kt"
    ).readText()

    @Test
    fun sectionDividerUsesCurrentMaterial3HorizontalDivider() {
        assertTrue(source.contains("HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)"))
        assertFalse(source.contains("\n            Divider(color = MaterialTheme.colorScheme.surfaceVariant)"))
    }
}
