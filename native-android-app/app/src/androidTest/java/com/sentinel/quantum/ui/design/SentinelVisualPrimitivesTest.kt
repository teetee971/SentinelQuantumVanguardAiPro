package com.sentinel.quantum.ui.design

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SentinelVisualPrimitivesTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun decorativeLayersMatchTheContentSizedHeroInAScrollContainer() {
        composeRule.setContent {
            MaterialTheme {
                Column(Modifier.width(280.dp).verticalScroll(rememberScrollState())) {
                    SentinelHeroVisual(
                        SentinelStatus.UNKNOWN, "Sentinel", "Données indisponibles",
                        modifier = Modifier.testTag("hero")
                    )
                }
            }
        }
        val hero = composeRule.onNodeWithTag("hero").fetchSemanticsNode().boundsInRoot
        assertTrue(hero.width > 0f && hero.height > 0f)
        listOf("sentinel_hero_particles", "sentinel_hero_orbits").forEach { tag ->
            val layer = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
            assertEquals(hero, layer.boundsInRoot)
            assertNull(layer.config.getOrNull(SemanticsProperties.ContentDescription))
            assertNull(layer.config.getOrNull(SemanticsProperties.Text))
        }
    }

    @Test
    fun heroAndChipAnnounceEachVisibleLabelOnce() {
        composeRule.setContent {
            MaterialTheme {
                Column {
                    SentinelHeroVisual(
                        SentinelStatus.UNKNOWN, "Sentinel", "Données indisponibles",
                        modifier = Modifier.testTag("hero")
                    )
                    SentinelStatusChip(SentinelStatus.LIMITED, modifier = Modifier.testTag("chip"))
                }
            }
        }
        listOf("Sentinel", "Données indisponibles", SentinelStatus.UNKNOWN.visual().label,
            SentinelStatus.LIMITED.visual().label).forEach { label ->
            composeRule.onAllNodesWithText(label).assertCountEquals(1)
        }
        listOf("hero", "chip").forEach { tag ->
            assertNull(composeRule.onNodeWithTag(tag).fetchSemanticsNode().config
                .getOrNull(SemanticsProperties.ContentDescription))
        }
        composeRule.onNodeWithTag("chip").assertTextEquals(SentinelStatus.LIMITED.visual().label)
    }

    @Test
    fun staticAndReducedMotionKeepContentAndActionsAtLargeFontScale() {
        var clicks = 0
        composeRule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 2f)) {
                MaterialTheme {
                    Column(Modifier.width(240.dp).verticalScroll(rememberScrollState())) {
                        listOf(MotionLevel.STATIC, MotionLevel.REDUCED).forEach { level ->
                            SentinelHeroVisual(
                                SentinelStatus.UNKNOWN, "Sentinel $level", "Données indisponibles",
                                motionLevel = level
                            )
                            SentinelActionTile(
                                "Ouvrir $level", "Détails", Icons.Default.Security,
                                onClick = { clicks++ }, modifier = Modifier.testTag("action_$level")
                            )
                        }
                    }
                }
            }
        }
        listOf(MotionLevel.STATIC, MotionLevel.REDUCED).forEach { level ->
            composeRule.onAllNodesWithText("Sentinel $level").assertCountEquals(1)
            composeRule.onNodeWithTag("action_$level").performScrollTo()
                .assertHasClickAction().performClick()
        }
        composeRule.runOnIdle { assertEquals(2, clicks) }
    }
}
