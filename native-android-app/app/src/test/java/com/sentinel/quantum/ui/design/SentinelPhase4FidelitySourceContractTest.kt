package com.sentinel.quantum.ui.design

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SentinelPhase4FidelitySourceContractTest {
    private fun source(name: String): String = File(
        "src/main/java/com/sentinel/quantum/ui/design/$name.kt"
    ).readText()

    private fun screenSource(name: String): String = File(
        "src/main/java/com/sentinel/quantum/ui/screens/$name.kt"
    ).readText()

    @Test
    fun rendererExposesReusableVolumetricMaterialsAndDepthPrimitives() {
        val scene = source("SentinelHolographicScene")

        assertTrue(scene.contains("data class SentinelSceneMaterial"))
        assertTrue(scene.contains("drawVolumetricSphere"))
        assertTrue(scene.contains("drawExtrudedShield"))
        assertTrue(scene.contains("drawPerspectivePhone"))
        assertTrue(scene.contains("drawHolographicCube"))
        assertTrue(scene.contains("drawContainmentCylinder"))
        assertTrue(scene.contains("drawThreatCoreVolume"))
        assertTrue(scene.contains("drawParticleField"))
    }

    @Test
    fun rendererHasASeparateNetworkTopologyComposition() {
        val scene = source("SentinelHolographicScene")
        val network = screenSource("NetworkSurveillanceScreen")

        assertTrue(scene.contains("NETWORK_TOPOLOGY"))
        assertTrue(scene.contains("drawNetworkTopology"))
        assertTrue(network.contains("SentinelHolographicScene"))
        assertTrue(network.contains("SentinelSceneKind.NETWORK_TOPOLOGY"))
    }

    @Test
    fun metricsUseAnAdaptiveGridInsteadOfFourNarrowColumns() {
        val panels = source("SentinelReferencePanels")

        assertTrue(panels.contains("chunked(2)"))
        assertFalse(panels.contains("metrics.take(4).forEach"))
        assertFalse(panels.contains("maxLines = 1"))
    }

    @Test
    fun sceneSpecificObjectsAreNotCollapsedIntoOnlyAnOrbitalWireframe() {
        val scene = source("SentinelHolographicScene")

        assertTrue(scene.contains("drawSceneMaterial"))
        assertTrue(scene.contains("drawDepthRings"))
        assertTrue(scene.contains("drawSpecularHighlight"))
        assertTrue(scene.contains("drawPerspectiveGrid"))
        assertTrue(scene.contains("drawParticleField"))
    }

    @Test
    fun sharedLegacyCoreAlsoUsesAStatusDrivenVolumetricObject() {
        val core = source("SentinelQuantumCore")

        assertTrue(core.contains("coreObservedStatus"))
        assertTrue(core.contains("drawCoreVolume"))
        assertTrue(core.contains("drawCorePerspectiveGrid"))
        assertTrue(core.contains("drawCoreShield"))
    }

    @Test
    fun phoneCoreDoesNotPlaceACommandHeroAboveTheKeypad() {
        val dialer = File("src/main/java/com/sentinel/quantum/SentinelDialerActivity.kt").readText()

        assertFalse(dialer.contains("SentinelQuantumCore("))
        assertTrue(dialer.contains("phone_core_call"))
        assertTrue(dialer.contains("PhoneCoreNumberPad"))
    }

    @Test
    fun activationUsesAReadableProgressBandInsteadOfARepetitiveCoreGraph() {
        val activation = File("src/main/java/com/sentinel/quantum/PhoneCoreActivationActivity.kt").readText()

        assertFalse(activation.contains("SentinelQuantumCore("))
        assertTrue(activation.contains("Progression réelle"))
        assertTrue(activation.contains("FlowRow"))
    }
}
