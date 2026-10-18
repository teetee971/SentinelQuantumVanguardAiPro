package com.sentinel.quantum.ui.design

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Source-level guard for the official D.1 phase-3 scene vocabulary. */
class SentinelHolographicSceneSourceContractTest {
    private val source: String by lazy {
        File("src/main/java/com/sentinel/quantum/ui/design/SentinelHolographicScene.kt")
            .takeIf(File::exists)
            ?.readText()
            .orEmpty()
    }

    @Test
    fun exposesOneSpecializedSceneKindForEachOfficialComposition() {
        listOf(
            "RISK_APPLICATIONS",
            "THREAT_GLOBE",
            "ANALYSIS_REPORT",
            "QUARANTINE_CHAMBER",
            "REAL_TIME_PROTECTION",
            "THREAT_DETAIL",
            "DEVICE_ANALYSIS",
            "COMMAND_CENTER",
            "VPN_GLOBE",
            "CONTACT_ORBIT",
            "SECURE_MESSAGES",
            "NUMBER_IDENTITY"
        ).forEach { kind ->
            assertTrue("Missing official scene kind $kind", source.contains(kind))
        }
    }

    @Test
    fun sceneModelCarriesTruthfulStatusAndEvidenceWithoutInventedMetrics() {
        assertTrue(source.contains("data class SentinelHolographicSceneModel"))
        assertTrue(source.contains("val status: SentinelStatus"))
        assertTrue(source.contains("val evidenceLabel: String"))
        assertTrue(source.contains("val evidenceSource: String"))
        assertTrue(source.contains("SentinelStatus.UNKNOWN"))
        assertTrue(source.contains("SentinelStatus.LIMITED"))
    }

    @Test
    fun sceneUsesMotionPolicyAndKeepsCanvasDecorationsSilentForTalkBack() {
        assertTrue(source.contains("motionLevel: MotionLevel"))
        assertTrue(source.contains("MotionLevel.FULL"))
        assertTrue(source.contains("MotionLevel.STATIC"))
        assertTrue(source.contains("MotionLevel.REDUCED"))
        assertTrue(source.contains("clearAndSetSemantics"))
        assertTrue(source.contains("Canvas("))
    }

    @Test
    fun sceneSupportsManualRotationWithoutMakingMotionRequired() {
        assertTrue(source.contains("detectDragGestures"))
        assertTrue(source.contains("rotationOffset"))
        assertTrue(source.contains("pointerInput"))
    }

    @Test
    fun sceneDoesNotUseReferenceImagesAsRuntimeContent() {
        assertTrue(source.contains("SentinelHolographicScene"))
        assertTrue(!source.contains("painterResource"))
        assertTrue(!source.contains("reference-source"))
        assertTrue(!source.contains("100041562"))
    }
}
