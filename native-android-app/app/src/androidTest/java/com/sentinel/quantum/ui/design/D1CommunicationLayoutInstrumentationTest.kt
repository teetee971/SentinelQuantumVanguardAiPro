package com.sentinel.quantum.ui.design

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.sentinel.quantum.ui.theme.SentinelQuantumTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class D1CommunicationLayoutInstrumentationTest {
    @get:Rule val rule = createComposeRule()

    @Test fun longFrenchHeadingFitsNarrowSurfaceAtTwoHundredPercentFont() {
        val title = "Votre répertoire et vos conversations locales"
        rule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 2f)) {
                SentinelQuantumTheme {
                    Column(Modifier.width(280.dp).verticalScroll(rememberScrollState())) {
                        SentinelCommunicationHeader("Communications", title,
                            "Accès aux contacts facultatif. Aucune confiance ni protection ne sont déduites de la présentation.")
                    }
                }
            }
        }
        rule.onNodeWithText(title).assertIsDisplayed()
        val header = rule.onNodeWithTag("d1-communication-header").fetchSemanticsNode().boundsInRoot
        val heading = rule.onNodeWithText(title).fetchSemanticsNode()
        assertTrue(heading.boundsInRoot.left >= header.left && heading.boundsInRoot.right <= header.right)
        assertTrue(heading.config.getOrNull(SemanticsProperties.Heading) != null)
    }
}
