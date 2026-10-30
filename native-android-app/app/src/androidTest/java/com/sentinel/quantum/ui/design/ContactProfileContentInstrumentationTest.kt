package com.sentinel.quantum.ui.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.semantics.SemanticsProperties
import org.junit.Assert.assertTrue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.sentinel.quantum.security.*
import com.sentinel.quantum.ui.theme.SentinelColorScheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Synthetic fixture only: no provider reads/writes, calls or real messages. */
class ContactProfileContentInstrumentationTest {
    @get:Rule val rule = createComposeRule()
    private val contact = LocalContactLookup.Contact(7, "Fixture Profile", listOf("01 23 45 67 89", "0123456789"), true)

    @Test fun duplicatedProviderNumberHasOneRowAndActionsTargetThatNumber() {
        var called: String? = null
        var messaged: String? = null
        rule.setContent {
            MaterialTheme(colorScheme = SentinelColorScheme) {
                D1ContactProfileContent(contact, ContactProfileSnapshot(ContactProfileRequest(7, null, 0), ContactProfileStatus.READY, listOf(
                    ContactProfileField(ContactProfileKind.PHONE, "", "01 23 45 67 89"),
                    ContactProfileField(ContactProfileKind.PHONE, "", "0123456789"))),
                    { called = it }, { messaged = it }, {}, {}, modifier = Modifier.fillMaxSize())
            }
        }
        rule.onAllNodesWithText("01 23 45 67 89").assertCountEquals(1)
        rule.onNodeWithText("Appeler").performScrollTo().performClick()
        rule.onNodeWithText("Message", useUnmergedTree = true).performScrollTo().performClick()
        rule.runOnIdle {
            assertEquals("01 23 45 67 89", called)
            assertEquals("01 23 45 67 89", messaged)
        }
    }


    @Test fun removedDirectoryNumberIsNotActionableAndNewProviderNumberIsShown() {
        val updated = ContactProfileSnapshot(ContactProfileRequest(7, null, 1), ContactProfileStatus.READY, listOf(
            ContactProfileField(ContactProfileKind.PHONE, "Mobile", "0987654321")
        ))
        var called: String? = null
        rule.setContent {
            MaterialTheme(colorScheme = SentinelColorScheme) {
                D1ContactProfileContent(contact, updated, { called = it }, {}, {}, {}, modifier = Modifier.fillMaxSize())
            }
        }
        rule.onNodeWithText("01 23 45 67 89").assertDoesNotExist()
        rule.onNodeWithText("0987654321").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Appeler").performScrollTo().performClick()
        rule.runOnIdle { assertEquals("0987654321", called) }
    }

    @Test fun emptyFreshContactDoesNotRestoreDirectoryPhoneActions() {
        rule.setContent {
            MaterialTheme(colorScheme = SentinelColorScheme) {
                D1ContactProfileContent(contact, ContactProfileSnapshot(ContactProfileRequest(7, null, 1), ContactProfileStatus.READY),
                    {}, {}, {}, {}, modifier = Modifier.fillMaxSize())
            }
        }
        rule.onNodeWithText("Appeler").assertDoesNotExist()
        rule.onNodeWithText("Message").assertDoesNotExist()
        rule.onNodeWithText("Aucun numéro renseigné").performScrollTo().assertIsDisplayed()
    }

    @Test fun expandedSheetAtLargeTextKeepsCloseAccessibleAfterLongNote() {
        val longNote = "Fixture line\n".repeat(200)
        val snapshot = ContactProfileSnapshot(ContactProfileRequest(7, null, 0), ContactProfileStatus.READY,
            listOf(ContactProfileField(ContactProfileKind.NOTE, "", longNote)))
        var dismissed = false
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                MaterialTheme(colorScheme = SentinelColorScheme) {
                    D1ContactProfileFrame(contact, snapshot, true, { dismissed = true }, {}, {}, {}, {})
                }
            }
        }
        rule.onNodeWithText("Source : carnet Android", substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Fermer").assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(true, dismissed) }
    }

    @Test fun draggingProfileHeaderDoesNotMoveOrDismissSheet() {
        val shown = androidx.compose.runtime.mutableStateOf(true)
        rule.setContent {
            MaterialTheme(colorScheme = SentinelColorScheme) {
                if (shown.value) {
                    D1ContactProfileFrame(contact, null, true, { shown.value = false }, {}, {}, {}, {})
                }
            }
        }
        val header = rule.onNodeWithText("Sentinel · Contacts")
        val before = header.fetchSemanticsNode().boundsInRoot.top
        header.performTouchInput {
            swipe(center, center + androidx.compose.ui.geometry.Offset(0f, 500f), durationMillis = 400)
        }
        header.assertIsDisplayed()
        val after = header.fetchSemanticsNode().boundsInRoot.top
        assertTrue("Header drag must not move the profile panel", kotlin.math.abs(after - before) <= 1f)
        rule.onNodeWithText("Fermer").performTouchInput { click() }
        rule.onNodeWithTag("contact-profile-scroll").assertDoesNotExist()
    }

    /** Diagnostic regression for the owner's end-of-page freeze report. Requires a real runtime. */
    @Test fun repeatedBottomGesturesAllowTouchCloseWithoutScrollingBack() {
        val snapshot = ContactProfileSnapshot(ContactProfileRequest(7, null, 0), ContactProfileStatus.READY, listOf(
            ContactProfileField(ContactProfileKind.NOTE, "", "Synthetic fixture line\n".repeat(100))
        ))
        val shown = androidx.compose.runtime.mutableStateOf(true)
        rule.setContent {
            MaterialTheme(colorScheme = SentinelColorScheme) {
                if (shown.value) {
                    D1ContactProfileFrame(contact, snapshot, true, { shown.value = false }, {}, {}, {}, {})
                }
            }
        }
        val scroll = rule.onNodeWithTag("contact-profile-scroll")
        rule.onNodeWithText("Source : carnet Android", substring = true).performScrollTo()
        repeat(3) { scroll.performTouchInput { swipeUp(durationMillis = 150) } }
        val range = scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue("Must stay at the content end before closing", range.value() >= range.maxValue() - 1f)
        // Touch injection exercises hit testing; performClick alone invokes the semantics action.
        // Compose synchronizes before input: this does not measure immediate response during a stall.
        rule.onNodeWithText("Fermer").assertIsDisplayed().performTouchInput { click() }
        rule.onNodeWithTag("contact-profile-scroll").assertDoesNotExist()
        rule.runOnIdle { assertEquals(false, shown.value) }
    }

    /** Diagnostic regression for the owner's end-of-page freeze report. Requires a real runtime. */
    @Test fun repeatedBottomGesturesStillAllowScrollingBackAndClosing() {
        val snapshot = ContactProfileSnapshot(ContactProfileRequest(7, null, 0), ContactProfileStatus.READY, listOf(
            ContactProfileField(ContactProfileKind.NOTE, "", "Synthetic fixture line\n".repeat(100))
        ))
        var dismissed = false
        rule.setContent {
            MaterialTheme(colorScheme = SentinelColorScheme) {
                D1ContactProfileFrame(contact, snapshot, true, { dismissed = true }, {}, {}, {}, {})
            }
        }
        val scroll = rule.onNodeWithTag("contact-profile-scroll")
        rule.onNodeWithText("Source : carnet Android", substring = true).performScrollTo()
        repeat(3) {
            scroll.performTouchInput { swipeUp(durationMillis = 150) }
            rule.waitForIdle()
        }
        val endRange = scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        val bottom = endRange.value()
        assertTrue("Must reach the content end", bottom >= endRange.maxValue() - 1f)
        scroll.performTouchInput { swipeDown(durationMillis = 400) }
        rule.waitForIdle()
        val afterRange = scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue("A reverse gesture at the bottom must scroll back", afterRange.value() < bottom)
        rule.onNodeWithText("Fermer").assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(true, dismissed) }
    }

    @Test fun importedDetailsAndBottomProvenanceAreReachableByScrolling() {
        val snapshot = ContactProfileSnapshot(ContactProfileRequest(7, null, 0), ContactProfileStatus.READY, listOf(
            ContactProfileField(ContactProfileKind.EMAIL, "Travail", "fixture@example.invalid"),
            ContactProfileField(ContactProfileKind.ADDRESS, "Personnel", "Fixture street"),
            ContactProfileField(ContactProfileKind.ORGANISATION, "Travail", "Fixture Company\nFixture Role"),
            ContactProfileField(ContactProfileKind.EVENT, "Anniversaire", "--04-12"),
            ContactProfileField(ContactProfileKind.NOTE, "", "Fixture note"),
            ContactProfileField(ContactProfileKind.WEBSITE, "Profil importé", "https://example.invalid")
        ))
        rule.setContent {
            MaterialTheme(colorScheme = SentinelColorScheme) {
                D1ContactProfileContent(contact, snapshot, {}, {}, {}, {}, modifier = Modifier.fillMaxSize())
            }
        }
        listOf("fixture@example.invalid", "Fixture street", "Fixture Company\nFixture Role", "--04-12", "Fixture note", "https://example.invalid").forEach {
            rule.onNodeWithText(it).performScrollTo().assertIsDisplayed()
        }
        rule.onNodeWithText("Source : carnet Android", substring = true).performScrollTo().assertIsDisplayed()
    }
}
