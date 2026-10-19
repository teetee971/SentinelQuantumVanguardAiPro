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

    @Test fun scanAndCleanupAlwaysReleaseBusyIndicators() {
        assertTrue(source.contains("scanError = null"))
        assertTrue(source.contains("scan = null"))
        assertTrue(source.contains("observedRole = null"))
        assertTrue(source.contains("observedBattery = null"))
        assertTrue(source.contains("cleanupRun = null"))
        assertTrue(source.contains("catch (cancelled: CancellationException)"))
        assertTrue(source.contains("isScanning = false"))
        assertTrue(source.contains("isCleanupBusy = false"))
        assertTrue(source.contains("finally {"))
    }

    @Test fun failuresAreVisibleWithoutClaimingSuccess() {
        assertTrue(source.contains("scanError?.let"))
        assertTrue(source.contains("cleanupError?.let"))
        assertTrue(source.contains("Aucun succès n’est présumé."))
    }

    @Test fun cleanupAndDiscoveryOutcomesAreIndependent() {
        assertTrue(source.contains("val completedRun = withContext(Dispatchers.IO)"))
        assertTrue(source.contains("cleanupRun = completedRun"))
        assertTrue(source.contains("Nettoyage effectué ; nouvel inventaire indisponible"))
        assertTrue(source.contains("cleanupDiscovery = null"))
    }

    @Test fun allEvidenceRemainsRenderedWithinScroll() {
        assertTrue(source.contains("result.evidence.forEach { evidence ->"))
        assertTrue(source.contains("EvidenceCard(evidence)"))
    }
}
