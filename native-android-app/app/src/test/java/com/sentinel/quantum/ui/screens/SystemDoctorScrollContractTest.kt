package com.sentinel.quantum.ui.screens

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for small devices: all scan evidence and storage actions must remain
 * reachable without nesting an unconstrained LazyColumn inside a vertical scroll.
 */
class SystemDoctorScrollContractTest {
    private val source = File(
        "src/main/java/com/sentinel/quantum/ui/screens/SystemDoctorScreen.kt"
    ).readText()

    @Test fun systemDoctorScreenHasSingleScrollableContainer() {
        assertTrue(source.contains("verticalScroll(rememberScrollState())"))
        assertFalse(source.contains("LazyColumn("))
    }

    @Test fun allEvidenceRemainsRenderedWithinScroll() {
        assertTrue(source.contains("result.evidence.forEach { evidence ->"))
        assertTrue(source.contains("EvidenceCard(evidence)"))
    }
}
