package com.sentinel.quantum.ui

import android.content.Context
import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import java.io.ByteArrayOutputStream
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

    @Test fun textLayoutOracleRejectsEllipsis() {
        val label = "ORACLE_LONG_TEXT_CANNOT_FIT"
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent { Text(label, Modifier.width(40.dp), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            compose.waitUntil(10_000) { compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty() }
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(label).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue("The real ellipsized layout must be rejected", layouts.any { visiblyClipped(it) })
            screenshot("negative-control-ellipsis")
        }
    }

    private fun visiblyClipped(layout: TextLayoutResult): Boolean =
        layout.multiParagraph.didExceedMaxLines || (0 until layout.lineCount).any { line ->
            layout.isLineEllipsized(line) || layout.getLineLeft(line) < -1f ||
                layout.getLineRight(line) > layout.size.width + 1f ||
                layout.getLineBottom(line) > layout.size.height + 1f
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
        // MultiParagraph.width is the available paragraph width, whereas size may
        // be the text's intrinsic width (e.g. SENTINEL: 67 vs 304 px). Comparing
        // those widths labels fully visible titles as overflow. Check laid-out
        // lines/ellipsis instead; 1 px accounts only for integer pixel rounding.
        if (layouts.any { visiblyClipped(it) }) screenshot("failure-${value.hashCode()}")
        assertFalse("$value is visually truncated: " + layouts.joinToString {
            "size=${it.size}, paragraph=${it.multiParagraph.width}x${it.multiParagraph.height}, lines=${it.lineCount}, ellipsis=" +
                (0 until it.lineCount).map { line -> it.isLineEllipsized(line) }
        }, layouts.any { visiblyClipped(it) })
    }

    private fun screenshot(name: String) {
        instrumentation.waitForIdleSync()
        val profile = InstrumentationRegistry.getArguments().getString("qualificationProfile", "default")
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val bytes = ByteArrayOutputStream()
        try {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes))
        } finally { bitmap.recycle() }
        assertTrue("Missing screenshot $name", bytes.size() > 256)
        if (Build.VERSION.SDK_INT >= 29) {
            // Public test evidence survives UTP uninstall; scoped MediaStore needs no
            // storage permission and does not weaken the production manifest.
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SentinelQualification/$profile")
            }
            val uri = requireNotNull(context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
            requireNotNull(context.contentResolver.openOutputStream(uri)).use { it.write(bytes.toByteArray()) }
        } else {
            val dir = File(context.getExternalFilesDir(null), "qualification/$profile").apply { mkdirs() }
            File(dir, "$name.png").writeBytes(bytes.toByteArray())
        }
    }
}
