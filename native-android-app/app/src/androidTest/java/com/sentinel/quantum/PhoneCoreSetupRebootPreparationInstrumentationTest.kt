package com.sentinel.quantum

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Observes an interrupted first-run session before the following workflow step reboots the AVD.
 *
 * The state is preserved only when the workflow explicitly passes preserve_state=true. Ordinary
 * connected-test runs clean up after themselves and therefore cannot leak wizard state.
 */
@RunWith(AndroidJUnit4::class)
class PhoneCoreSetupRebootPreparationInstrumentationTest {
    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()
    private val context: Context
        get() = instrumentation.targetContext
    private val preserveState: Boolean
        get() = InstrumentationRegistry.getArguments().getString("preserve_state") == "true"

    @Before
    fun clearWizardHistory() {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun cleanUpUnlessWorkflowWillReboot() {
        dismissSystemSetupDialog()
        if (!preserveState) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test
    fun leavesInterruptedSetupForRebootQualification() {
        val wizard = PhoneCoreSetupWizardStore(context)
        wizard.markInProgress()

        val scenario = ActivityScenario.launch<PhoneCoreActivationActivity>(
            Intent(context, PhoneCoreActivationActivity::class.java)
                .putExtra(PhoneCoreActivationActivity.EXTRA_FIRST_RUN_SETUP, true)
        )
        try {
            // The real first-run UI may immediately launch an Android-owned role/permission
            // surface. ActivityScenario is then legitimately PAUSED until that surface returns;
            // forcing RESUMED first makes the qualification fail before observing persistence.
            waitForAttemptedTarget()
            dismissSystemSetupDialog()
            instrumentation.waitForIdleSync()
        } finally {
            scenario.close()
        }

        val attemptedBeforeStop = waitForAttemptedTarget()
        assertNotNull("real activation UI must persist its current target before reboot", attemptedBeforeStop)
        instrumentation.uiAutomation.executeShellCommand("am force-stop ${context.packageName}").use { }
        val persisted = PhoneCoreSetupWizardStore(context)
        assertEquals(PhoneCoreSetupWizardStore.LifecycleState.IN_PROGRESS, persisted.lifecycleState())
        assertEquals(attemptedBeforeStop, persisted.attemptedTargetKey())
        check(!persisted.isCompleted()) { "reboot preparation must never manufacture COMPLETED" }
    }

    private fun dismissSystemSetupDialog() {
        // Closing ActivityScenario does not own Android's role/permission surface. If the setup
        // launch opened one, release it before the next instrumentation class or the following
        // reboot step receives input behind a stale system dialog.
        repeat(50) {
            val owner = instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString()
            if (owner in setOf(
                    "com.android.permissioncontroller",
                    "com.google.android.permissioncontroller",
                    "com.android.packageinstaller",
                    "com.google.android.packageinstaller",
                    "com.android.server.telecom"
                )) {
                instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                instrumentation.waitForIdleSync()
                return
            }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(100)
        }
    }

    private fun waitForAttemptedTarget(): String? {
        repeat(50) {
            PhoneCoreSetupWizardStore(context).attemptedTargetKey()?.let { return it }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(100)
        }
        return PhoneCoreSetupWizardStore(context).attemptedTargetKey()
    }

    private companion object {
        const val PREFS = "phone_core_setup_wizard_v2"
    }
}
