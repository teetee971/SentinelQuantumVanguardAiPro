package com.sentinel.quantum.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.sentinel.quantum.MainActivity
import com.sentinel.quantum.PhoneCoreActivationActivity
import com.sentinel.quantum.PhoneCoreSetupWizardStore
import com.sentinel.quantum.SentinelDialerActivity
import com.sentinel.quantum.SmsComposeActivity
import java.io.File
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Real activities and real callbacks. Screenshots supplement assertions; they never replace them. */
@OptIn(ExperimentalTestApi::class)
class MainNavigationQualificationTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Before fun deferOnlyTheAutomaticWizard() {
        context.getSharedPreferences("phone_core_setup_wizard_v2", Context.MODE_PRIVATE).edit().clear().commit()
        PhoneCoreSetupWizardStore(context).markDeferred()
    }

    @Test fun realBottomNavigationBackAndRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            title("SENTINEL")
            screenshot("home")
            for ((tag, expectedTitle) in listOf(
                "communications" to "Téléphone, SMS/MMS & canaux externes",
                "protection" to "Posture locale & Phone Core",
                "more" to "Préférences locales & confidentialité"
            )) {
                compose.onNodeWithTag("main_nav_$tag").assertIsDisplayed().assertHasClickAction().performClick()
                title(expectedTitle)
                compose.onNodeWithTag("main_nav_$tag").assertIsSelected()
                screenshot(tag)
                scenario.recreate()
                title(expectedTitle)
                compose.onNodeWithTag("main_nav_$tag").assertIsSelected()
                screenshot("$tag-recreated")
                // Invoke Android's own dispatcher, not a synthetic navController mutation.
                scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                title("SENTINEL")
                compose.onNodeWithTag("main_nav_home").assertIsSelected()
            }
        }
        ActivityScenario.launch(MainActivity::class.java).use { title("SENTINEL"); screenshot("home-relaunch") }
    }

    @Test fun phoneCoreDialerAndSmsSurfacesAreVisibleAfterRecreation() {
        ActivityScenario.launch(PhoneCoreActivationActivity::class.java).use { scenario ->
            title("Centre d’activation")
            screenshot("phone-core")
            scenario.recreate()
            title("Centre d’activation")
        }
        ActivityScenario.launch(SentinelDialerActivity::class.java).use { scenario ->
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("phone_core_tab_0").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("phone_core_tab_0").assertIsDisplayed().performClick()
            screenshot("dialer-keypad")
            scenario.recreate()
            compose.onNodeWithTag("phone_core_tab_0").assertIsDisplayed()
        }
        ActivityScenario.launch(SmsComposeActivity::class.java).use { scenario ->
            title("Messages Sentinel")
            screenshot("sms")
            scenario.recreate()
            title("Messages Sentinel")
        }
    }

    private fun title(value: String, substring: Boolean = false) {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("main_brand_loading").fetchSemanticsNodes().isEmpty() }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(value, substring = substring, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        val node = compose.onAllNodesWithText(value, substring = substring, useUnmergedTree = true)[0]
        node.assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("$value has no text layout", layouts.isNotEmpty())
        assertFalse("$value is visually truncated", layouts.any { it.hasVisualOverflow })
    }

    private fun screenshot(name: String) {
        instrumentation.waitForIdleSync()
        val profile = InstrumentationRegistry.getArguments().getString("qualificationProfile", "default")
        val dir = File(context.getExternalFilesDir(null), "qualification/$profile").apply { mkdirs() }
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            File(dir, "$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { bitmap.recycle() }
        assertTrue("Missing screenshot $name", File(dir, "$name.png").length() > 256)
    }
}
