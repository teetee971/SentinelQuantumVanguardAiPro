package com.sentinel.quantum.ui.design

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the compact evidence panels used to reproduce the official D.1 compositions. */
class SentinelReferencePanelsSourceContractTest {
    private val source: String by lazy {
        File("src/main/java/com/sentinel/quantum/ui/design/SentinelReferencePanels.kt")
            .takeIf(File::exists)
            ?.readText()
            .orEmpty()
    }

    @Test
    fun exposesCompactMetricStripAndActionGrid() {
        assertTrue(source.contains("data class SentinelReferenceMetric"))
        assertTrue(source.contains("fun SentinelReferenceMetricStrip"))
        assertTrue(source.contains("fun SentinelReferenceActionGrid"))
    }

    @Test
    fun keepsMetricValuesUntruncatedAndUsesStableStatusColors() {
        assertTrue(source.contains("visual().role"))
        assertTrue(source.contains("softWrap = true"))
        assertTrue(!source.contains("maxLines = 1"))
        assertTrue(source.contains("SentinelD1.BorderStrong"))
    }

    @Test
    fun exposesPanelsToAccessibility() {
        assertTrue(source.contains("contentDescription"))
        assertTrue(source.contains("Role.Button"))
    }
}
