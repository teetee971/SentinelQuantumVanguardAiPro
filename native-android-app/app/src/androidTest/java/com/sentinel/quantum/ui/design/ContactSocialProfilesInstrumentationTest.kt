package com.sentinel.quantum.ui.design

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.AnnotatedString
import com.sentinel.quantum.security.*
import com.sentinel.quantum.ui.theme.SentinelColorScheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real form, synthetic data and callbacks: never launches browser or touches private contacts. */
class ContactSocialProfilesInstrumentationTest {
    @get:Rule val rule = createComposeRule()
    private val owner = ContactSocialProfiles.ownerKey("synthetic-lookup-A")!!

    @Composable private fun Form(ownerKey: String = owner, displayName: String = "Fixture Person",
        onSearch: (SocialNetwork, String, String) -> Unit = { _, _, _ -> },
        onAssociate: (SocialNetwork, String, String) -> Unit = { _, _, _ -> }) {
        MaterialTheme(colorScheme = SentinelColorScheme) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState(), overscrollEffect = null)) {
                ContactSocialProfilesContent(ownerKey, displayName, ContactSocialLinksResult(true), false, null,
                    onSearch, onAssociate, {}, {})
            }
        }
    }

    @Test fun noSearchOrAssociationUntilExplicitGestureAndConfirmation() {
        var searches = 0
        var associations = 0
        var chosen: String? = null
        rule.setContent { Form(onSearch = { _, _, _ -> searches++ }, onAssociate = { _, url, _ -> associations++; chosen = url }) }
        rule.runOnIdle { assertEquals(0, searches); assertEquals(0, associations) }
        rule.onNodeWithText("Rechercher ou associer un profil").performScrollTo().performClick()
        rule.onNodeWithText("Entreprise (facultative)").performScrollTo().performTextInput("Fixture Company")
        rule.onNodeWithText("Rechercher dans le navigateur").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(1, searches); assertEquals(0, associations) }
        rule.onNodeWithText("URL du profil choisi").performScrollTo().performTextInput("https://linkedin.com/in/fixture-person")
        rule.onNodeWithText("Examiner la liaison").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(0, associations) }
        rule.onNodeWithText("Annuler").performClick()
        rule.runOnIdle { assertEquals(0, associations) }
        rule.onNodeWithText("Examiner la liaison").performScrollTo().performClick()
        rule.onNodeWithText("Confirmer la liaison").performClick()
        rule.runOnIdle { assertEquals(1, associations); assertEquals("https://www.linkedin.com/in/fixture-person/", chosen) }
    }

    @Test fun changingContactDropsOldConfirmationAndDraft() {
        var current by mutableStateOf(owner)
        var associations = 0
        rule.setContent { Form(current, if (current == owner) "Fixture A" else "Fixture B", onAssociate = { _, _, _ -> associations++ }) }
        rule.onNodeWithText("Rechercher ou associer un profil").performScrollTo().performClick()
        rule.onNodeWithText("URL du profil choisi").performScrollTo().performTextInput("https://linkedin.com/in/fixture-a")
        rule.onNodeWithText("Examiner la liaison").performScrollTo().performClick()
        rule.runOnIdle { current = ContactSocialProfiles.ownerKey("synthetic-lookup-B")!! }
        rule.onNodeWithText("Associer ce profil ?").assertDoesNotExist()
        rule.onNodeWithText("Rechercher ou associer un profil").performScrollTo().performClick()
        rule.onNodeWithText("Nom à rechercher").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("Fixture B")))
        rule.onNodeWithText("URL du profil choisi").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        rule.runOnIdle { assertEquals(0, associations) }
    }

    @Test fun recreationKeepsSearchDraftButNeverRestoresPendingConfirmation() {
        val restoration = StateRestorationTester(rule)
        var associations = 0
        restoration.setContent { Form(onAssociate = { _, _, _ -> associations++ }) }
        rule.onNodeWithText("Rechercher ou associer un profil").performScrollTo().performClick()
        rule.onNodeWithText("Entreprise (facultative)").performScrollTo().performTextInput("Fixture Company")
        rule.onNodeWithText("URL du profil choisi").performScrollTo().performTextInput("https://linkedin.com/in/fixture-person")
        rule.onNodeWithText("Examiner la liaison").performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithText("Associer ce profil ?").assertDoesNotExist()
        rule.onNodeWithText("Entreprise (facultative)").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("Fixture Company")))
        rule.runOnIdle { assertEquals(0, associations) }
    }
}
