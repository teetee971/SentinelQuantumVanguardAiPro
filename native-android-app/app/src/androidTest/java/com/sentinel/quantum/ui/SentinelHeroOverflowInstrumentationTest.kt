package com.sentinel.quantum.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.sentinel.quantum.ui.design.SentinelHero
import com.sentinel.quantum.ui.theme.SentinelQuantumTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SentinelHeroOverflowInstrumentationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun badgesStayInsideCompactViewport() {
        composeRule.setContent {
            SentinelQuantumTheme {
                Box(Modifier.width(180.dp).testTag("hero-viewport")) {
                    SentinelHero(
                        eyebrow = "État",
                        title = "Protection",
                        body = "État local observé.",
                        badges = listOf(
                            "État Android très long",
                            "Traitement local très long"
                        ).map { it to androidx.compose.ui.graphics.Color.Cyan }
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val viewport = composeRule.onNodeWithTag("hero-viewport")
            .fetchSemanticsNode()
            .boundsInRoot
        listOf("ÉTAT ANDROID TRÈS LONG", "TRAITEMENT LOCAL TRÈS LONG").forEach { label ->
            val badge = composeRule.onNodeWithText(label, useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
            assertTrue("$label dépasse à gauche", badge.left >= viewport.left)
            assertTrue("$label dépasse à droite", badge.right <= viewport.right)
        }
    }
}
