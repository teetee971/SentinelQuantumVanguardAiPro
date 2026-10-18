package com.sentinel.quantum.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sentinel.quantum.CallerIdActivity
import com.sentinel.quantum.MainActivity
import com.sentinel.quantum.PhoneCoreSetupWizardStore
import com.sentinel.quantum.SentinelDialerActivity
import com.sentinel.quantum.SmsComposeActivity
import com.sentinel.quantum.navigation.Screen
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Produces Android-runtime screenshots for the official D.1 fidelity review.
 *
 * The test is intentionally separate from visual contract tests: a passing contract test is
 * not a screenshot, and this file writes the actual display capture to app-private storage so
 * CI can pull it with `run-as` and preserve the exact runtime provenance.
 */
@RunWith(AndroidJUnit4::class)
class D1VisualScreenshotInstrumentationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext.applicationContext

    @Test fun reference01NumberIdentityIsCaptured() = captureCallerId("01-fiche-numero")

    @Test fun reference02SecureMessagesIsCaptured() =
        captureActivity<SmsComposeActivity>("02-messages-securises") {
            putExtra(SmsComposeActivity.EXTRA_OPEN_CONVERSATIONS, true)
        }

    @Test fun reference03ContactsIsCaptured() =
        captureActivity<SentinelDialerActivity>("03-contacts-sentinel") {
            putExtra(SentinelDialerActivity.EXTRA_OPEN_CONTACTS, true)
        }

    @Test fun reference04VpnIsCaptured() = captureRoute(Screen.Vpn.route, "04-vpn-prive")

    /** The official planche is cross-screen; this capture records the representative D.1 shell. */
    @Test fun reference05GlobalPlancheShellIsCaptured() =
        captureRoute(Screen.Home.route, "05-planche-coherence-shell")

    @Test fun reference06CommandCenterIsCaptured() =
        captureRoute(Screen.Home.route, "06-command-center")

    @Test fun reference07DeviceAnalysisIsCaptured() =
        captureRoute(Screen.SystemDoctor.route, "07-analyse-appareil")

    @Test fun reference08ThreatDetailIsCaptured() =
        captureRoute(Screen.ThreatDetail.route, "08-detail-menace")

    @Test fun reference09RealtimeProtectionIsCaptured() =
        captureRoute(Screen.PhoneSecurity.route, "09-protection-temps-reel")

    @Test fun reference10QuarantineIsCaptured() =
        captureRoute(Screen.Quarantine.route, "10-quarantaine-securisee")

    @Test fun reference11RiskApplicationsIsCaptured() =
        captureRoute(Screen.AppPermissionAnalyzer.route, "11-applications-a-risque")

    @Test fun reference12ThreatMapIsCaptured() =
        captureRoute(Screen.ThreatMap.route, "12-carte-menaces")

    @Test fun reference13AnalysisReportIsCaptured() =
        captureRoute(Screen.SecurityAudit.route, "13-rapport-analyse")

    private fun captureRoute(route: String, name: String) {
        captureActivity<MainActivity>(name) {
            putExtra(MainActivity.EXTRA_D1_CAPTURE, true)
            putExtra(MainActivity.EXTRA_D1_ROUTE, route)
        }
    }

    private fun captureCallerId(name: String) {
        captureActivity<CallerIdActivity>(name) {
            // Reserved documentation number: it exercises the unknown/shared-prefix state
            // without presenting a real subscriber identity or triggering ARCEP lookup.
            putExtra(CallerIdActivity.EXTRA_NUMBER, "+12025550123")
            putExtra(CallerIdActivity.EXTRA_COUNTRY, "US/CA")
            putExtra(CallerIdActivity.EXTRA_TYPE, "UNKNOWN")
            putExtra(CallerIdActivity.EXTRA_VERIFICATION, "UNKNOWN")
            putExtra(CallerIdActivity.EXTRA_SOURCE, "Capture de qualification — aucune identité affirmée")
            putExtra(CallerIdActivity.EXTRA_IDENTITY_VERIFIED, false)
        }
    }

    private inline fun <reified T : Activity> captureActivity(
        name: String,
        crossinline configure: Intent.() -> Unit = {}
    ) {
        resetFirstRunState()
        val intent = Intent(context, T::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            configure()
        }
        val scenario = ActivityScenario.launch<T>(intent)
        try {
            SystemClock.sleep(2_000)
            // Several D.1 scenes intentionally keep a bounded holographic animation active.
            // waitForIdleSync() would wait forever on those frames, so use a bounded delay and
            // a main-thread barrier that does not require the choreographer to become idle.
            instrumentation.runOnMainSync { }
            SystemClock.sleep(250)
            writeDisplayCapture(name)
        } finally {
            scenario.close()
        }
    }

    private fun resetFirstRunState() {
        context.getSharedPreferences("phone_core_setup_wizard_v2", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        PhoneCoreSetupWizardStore(context).markDeferred()
    }

    private fun writeDisplayCapture(name: String) {
        val directory = File(context.filesDir, CAPTURE_DIRECTORY).apply { mkdirs() }
        val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot()) {
            "UiAutomation returned no display capture for $name"
        }
        File(directory, "$name.png").outputStream().use { output ->
            check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                "Could not encode display capture for $name"
            }
        }
        screenshot.recycle()
    }

    private companion object {
        const val CAPTURE_DIRECTORY = "d1-captures"
    }
}
