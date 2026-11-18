package com.sentinel.quantum.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runAndroidComposeUiTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.sentinel.quantum.BuildConfig
import com.sentinel.quantum.MainActivity
import com.sentinel.quantum.PhoneCoreActivationActivity
import com.sentinel.quantum.R
import org.junit.Assume.assumeTrue
import org.junit.Test

class CommercialPhoneCoreNavigationInstrumentationTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun launchAndProtectionActionsStayInsideTheCommercialGraph() {
        assumeTrue(BuildConfig.COMMERCIAL_PHONE_CORE_V1)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(context.packageName == "com.sentinel.quantum.v1validation")
        val monitor = ActivityLifecycleMonitorRegistry.getInstance()
        val dismissFirstRun = ActivityLifecycleCallback { activity, stage ->
            if (stage == Stage.CREATED && activity is PhoneCoreActivationActivity &&
                activity.intent.getBooleanExtra(PhoneCoreActivationActivity.EXTRA_FIRST_RUN_SETUP, false)
            ) {
                // End the real first-run activity as cancelled before any system role prompt.
                activity.finish()
            }
        }
        instrumentation.runOnMainSync { monitor.addLifecycleCallback(dismissFirstRun) }
        try {
            runAndroidComposeUiTest<MainActivity> {
                onNodeWithText("Ouvrir l’assistant d’activation").assertExists()
                onNodeWithText("Identifier un numéro").performClick()
                onNodeWithText("Recherche téléphonique").assertExists()

                onNodeWithText(context.getString(R.string.nav_protection)).performClick()
                onNodeWithText("Règles de filtrage").performClick()
                onNodeWithText(context.getString(R.string.call_blocking_title)).assertExists()
                onNodeWithText(context.getString(R.string.caller_id_remote_title)).assertDoesNotExist()

                onNodeWithText(context.getString(R.string.nav_communications)).performClick()
                onNodeWithText("Actions essentielles").assertExists()
                onNodeWithText("Canaux externes", substring = true).assertDoesNotExist()

                onNodeWithText(context.getString(R.string.nav_settings)).performClick()
                onNodeWithText("Diagnostic Phone Core").assertExists()
                onNodeWithText("Politique de confidentialité").assertExists()
            }
        } finally {
            instrumentation.runOnMainSync { monitor.removeLifecycleCallback(dismissFirstRun) }
        }
    }
}
