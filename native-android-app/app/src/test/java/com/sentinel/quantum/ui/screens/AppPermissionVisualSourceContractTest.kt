package com.sentinel.quantum.ui.screens

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** SDK-free guard against exposing two TalkBack actions for one expansion state. */
class AppPermissionVisualSourceContractTest {
    private val source = File(
        "src/main/java/com/sentinel/quantum/ui/screens/AppPermissionAnalyzerScreen.kt"
    ).readText()

    private val profileCard = source
        .substringAfter("private fun AppPermissionProfileCard")
        .substringBefore("@Composable\nprivate fun PermissionRiskBadge")

    @Test
    fun expansionHasOneAccessibleActionOwner() {
        assertTrue(
            "The profile card must own expansion as a semantic button action",
            profileCard.contains(".clickable(role = Role.Button, onClick = { expanded = !expanded })")
        )
        assertFalse(
            "The expansion indicator must not expose a second interactive control",
            profileCard.contains("IconButton(")
        )
        assertTrue(
            "The decorative expansion indicator must be silent to accessibility services",
            profileCard.contains("contentDescription = null")
        )
    }

    @Test
    fun expansionStateIsExposedToAccessibilityServices() {
        assertTrue(
            "The expansion card must expose an explicit state description",
            profileCard.contains(".semantics {")
        )
        assertTrue(
            "Expanded and collapsed states must be announced distinctly",
            profileCard.contains("stateDescription = if (expanded) \"Développée\" else \"Réduite\"")
        )
    }
}
