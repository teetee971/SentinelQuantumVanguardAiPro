package com.sentinel.quantum.ui.design

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** SDK-free guards for the reviewed regressions; runtime layout is covered by Compose tests. */
class SentinelVisualPrimitivesSourceTest {
    private fun source(name: String) = File(
        "src/main/java/com/sentinel/quantum/ui/design/$name.kt"
    ).readText()

    private fun screenSource(name: String) = File(
        "src/main/java/com/sentinel/quantum/ui/screens/$name.kt"
    ).readText()

    private fun effect(name: String) = source("SentinelVisualEffects")
        .substringAfter("fun $name(").substringBefore("\n/**")

    @Test
    fun sectionHeaderHasOneCanonicalD1Contract() {
        val glassComponents = source("SentinelGlassComponents")
        val chrome = File(
            "src/main/java/com/sentinel/quantum/ui/design/SentinelChrome.kt"
        ).readText()

        assertTrue("D.1 section headers must support explanatory subtitles", glassComponents.contains("subtitle: String?"))
        assertTrue("D.1 section headers must support compact trailing context", glassComponents.contains("trailing: String?"))
        assertFalse("The legacy chrome file must not define a second section-header implementation", chrome.contains("fun SentinelSectionHeader("))
    }

    @Test
    fun screenMotionPoliciesRefreshWhenTheScreenResumes() {
        listOf("SecurityAuditScreen", "AppPermissionAnalyzerScreen").forEach { name ->
            val screen = screenSource(name)
            assertFalse(
                "$name must not freeze motion policy for the whole composition",
                screen.contains("val motionLevel = remember(context) {"),
            )
            assertTrue(
                "$name must observe ON_RESUME to refresh motion policy",
                screen.contains("Lifecycle.Event.ON_RESUME"),
            )
            assertTrue(
                "$name must dispose its lifecycle observer",
                screen.contains("onDispose"),
            )
            assertTrue(
                "$name must key the motion policy to its resume epoch",
                screen.contains("remember(context, motionEpoch)"),
            )
        }
    }

    @Test
    fun decorativeLayersUseTheMeasuredHeroBounds() {
        val hero = source("SentinelHeroVisual")
        assertEquals("Both background layers must follow the content-sized Box", 2,
            Regex("\\.matchParentSize\\(\\)").findAll(hero).count())
        assertFalse("fillMaxSize cannot size a Canvas in an unbounded scroll axis",
            hero.contains(".fillMaxSize()"))
        assertTrue("Round corners must clip the decorative layers", hero.contains(".clip(shape)"))
    }

    @Test
    fun heroMergesVisibleTextWithoutRepeatingItAsADescription() {
        val hero = source("SentinelHeroVisual")
        assertTrue(hero.contains("semantics(mergeDescendants = true)"))
        assertFalse("Visible title/subtitle/status must not be announced twice",
            Regex("contentDescription\\s*=\\s*\"").containsMatchIn(hero))
    }

    @Test
    fun statusChipMergesItsLabelWithoutAnExtraAnnouncement() {
        val chip = source("SentinelGlassComponents")
            .substringAfter("fun SentinelStatusChip(").substringBefore("\n@Composable")
        assertTrue(chip.contains("semantics(mergeDescendants = true)"))
        assertFalse(chip.contains("contentDescription ="))
    }

    @Test
    fun animationValuesAreReadOnlyInTheDrawPhase() {
        listOf("SentinelParticleField", "SentinelOrbitField").forEach { name ->
            val body = effect(name)
            val composition = body.substringBefore("Canvas(")
            assertFalse("$name must keep the animation State until drawing",
                Regex("\\bby\\b|\\.value\\b").containsMatchIn(composition))
            assertTrue("$name must read the animation inside Canvas",
                body.substringAfter("Canvas(").contains("phase.value"))
        }
    }

    @Test
    fun particleSeedsAndRingStrokesAreCachedOutsideDrawing() {
        val particles = effect("SentinelParticleField")
        val orbits = effect("SentinelOrbitField")
        assertTrue("Deterministic particle data should not be rebuilt on each frame",
            particles.substringBefore("Canvas(").contains("remember(boundedCount, accent)"))
        assertTrue(orbits.substringBefore("Canvas(").contains("remember(accent)"))
        assertFalse("Stroke objects must not be allocated on every frame",
            orbits.substringAfter("Canvas(").contains("Stroke("))
    }

    @Test
    fun particlesAreInsetAndDecorationsAreClippedAndSilent() {
        val particles = effect("SentinelParticleField")
        assertTrue(particles.contains("coerceIn(radius, size.width - radius)"))
        assertTrue(particles.contains("coerceIn(radius, size.height - radius)"))
        assertTrue("Tiny canvases must also contain the whole particle",
            particles.contains("coerceAtMost(size.minDimension / 2f)"))
        listOf(particles, effect("SentinelOrbitField")).forEach { body ->
            assertTrue(body.contains(".clipToBounds()"))
            assertTrue(body.contains(".clearAndSetSemantics { }"))
        }
    }
}
