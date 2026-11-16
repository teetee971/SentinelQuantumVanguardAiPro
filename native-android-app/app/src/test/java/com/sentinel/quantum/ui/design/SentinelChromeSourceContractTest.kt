package com.sentinel.quantum.ui.design

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SentinelChromeSourceContractTest {
    @Test
    fun `top bar back icon uses auto mirrored directional icon`() {
        val source = File("src/main/java/com/sentinel/quantum/ui/design/SentinelChrome.kt").readText()

        assertFalse(source.contains("androidx.compose.material.icons.filled.ArrowBack"))
        assertFalse(source.contains("Icons.Default.ArrowBack"))
        assertTrue(source.contains("androidx.compose.material.icons.automirrored.filled.ArrowBack"))
        assertTrue(source.contains("Icons.AutoMirrored.Filled.ArrowBack"))
    }
}
