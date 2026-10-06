package com.sentinel.quantum

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves first-run setup persistence/resume without weakening the production manifest.
 *
 * PhoneCoreActivationActivity is intentionally exported=false. ActivityScenario launches it from
 * the instrumentation boundary, so CI never needs to expose this internal activation surface to
 * the adb shell or to another application.
 */
@RunWith(AndroidJUnit4::class)
class PhoneCoreSetupResumeInstrumentationTest {
    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()
    private val context: Context
        get() = instrumentation.targetContext
    private val prefs
        get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Before
    fun clearWizardHistory() {
        prefs.edit().clear().commit()
    }

    @After
    fun cleanUpWizardHistoryAndDialogs() {
        runCatching {
            dismissSystemSetupDialog()
        }
        prefs.edit().clear().commit()
    }

    @Test
    fun wizardLifecyclePersistsWithoutManufacturingRuntimeReadiness() {
        assertEquals(PhoneCoreSetupWizardStore.LifecycleState.NOT_STARTED, PhoneCoreSetupWizardStore(context).lifecycleState())
        PhoneCoreSetupWizardStore(context).markOffered()
        assertEquals(PhoneCoreSetupWizardStore.LifecycleState.OFFERED, PhoneCoreSetupWizardStore(context).lifecycleState())
        PhoneCoreSetupWizardStore(context).markInProgress()
        PhoneCoreSetupWizardStore(context).markAttemptedTarget("role:android.app.role.DIALER")
        assertEquals(PhoneCoreSetupWizardStore.LifecycleState.IN_PROGRESS, PhoneCoreSetupWizardStore(context).lifecycleState())
        assertEquals("role:android.app.role.DIALER", PhoneCoreSetupWizardStore(context).attemptedTargetKey())
        PhoneCoreSetupWizardStore(context).markDeferred()
        assertEquals(PhoneCoreSetupWizardStore.LifecycleState.DEFERRED, PhoneCoreSetupWizardStore(context).lifecycleState())
        assertFalse(prefs.getBoolean(KEY_COMPLETED, false))
        // Completion is presentation history. Real Android facts still decide readiness.
        val before = PhoneCoreRuntimeFacts.read(context)
        PhoneCoreSetupWizardStore(context).markCompleted()
        assertEquals(PhoneCoreSetupWizardStore.LifecycleState.COMPLETED, PhoneCoreSetupWizardStore(context).lifecycleState())
        assertEquals(null, PhoneCoreSetupWizardStore(context).attemptedTargetKey())
        assertEquals(before, PhoneCoreRuntimeFacts.read(context))
        if (!PhoneCoreSetupWizardStore.softwarePrerequisitesReady(before)) {
            org.junit.Assert.assertTrue(PhoneCoreSetupWizardStore.shouldOpenSetup(true, before))
        }
        PhoneCoreSetupWizardStore(context).markInProgress()
        assertFalse("repair must invalidate legacy completed=true", prefs.getBoolean(KEY_COMPLETED, false))
        assertEquals(PhoneCoreSetupWizardStore.LifecycleState.IN_PROGRESS, PhoneCoreSetupWizardStore(context).lifecycleState())
    }

    @Test
    fun interruptedFirstRunResumesWithoutFalseCompletion() {
        val intent = Intent(context, PhoneCoreActivationActivity::class.java)
            .putExtra(PhoneCoreActivationActivity.EXTRA_FIRST_RUN_SETUP, true)

        val firstScenario = ActivityScenario.launch<PhoneCoreActivationActivity>(intent)
        try {
            val attemptedTarget = waitForAttemptedTarget()
            assertNotNull("first-run setup must persist the target it attempted", attemptedTarget)
            assertFalse("interrupted setup must never persist completed=true", prefs.getBoolean(KEY_COMPLETED, false))

            dismissSystemSetupDialog()
        } finally {
            firstScenario.close()
        }

        val persistedAttempt = prefs.getString(KEY_ATTEMPTED_TARGET, null)
        assertNotNull("interrupted setup must retain its attempted target for resume", persistedAttempt)
        assertFalse("interruption must not manufacture completion", prefs.getBoolean(KEY_COMPLETED, false))

        val resumedScenario = ActivityScenario.launch<PhoneCoreActivationActivity>(intent)
        try {
            resumedScenario.moveToState(Lifecycle.State.RESUMED)
            instrumentation.waitForIdleSync()
            SystemClock.sleep(300)

            resumedScenario.onActivity { activity ->
                assertFalse("resumed setup activity finished unexpectedly", activity.isFinishing)
            }
            assertEquals(
                "resume must preserve the same unresolved target instead of skipping ahead",
                persistedAttempt,
                prefs.getString(KEY_ATTEMPTED_TARGET, null)
            )
            assertFalse(
                "resumed incomplete setup must remain fail-closed",
                prefs.getBoolean(KEY_COMPLETED, false)
            )
        } finally {
            resumedScenario.close()
        }
    }

    private fun waitForAttemptedTarget(): String? {
        repeat(50) {
            prefs.getString(KEY_ATTEMPTED_TARGET, null)?.let { return it }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(100)
        }
        return prefs.getString(KEY_ATTEMPTED_TARGET, null)
    }

    private fun dismissSystemSetupDialog() {
        // A delayed global Back can hit Sentinel after Android has already closed
        // its role dialog, destroying the resumed wizard instead of interrupting setup.
        // Only dismiss an observed system permission/role window; scenario.close()
        // still supplies the interruption when no system dialog is visible.
        repeat(50) {
            val owner = instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString()
            if (owner in setOf("com.android.permissioncontroller", "com.google.android.permissioncontroller",
                    "com.android.packageinstaller", "com.google.android.packageinstaller",
                    "com.android.server.telecom")) {
                instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                instrumentation.waitForIdleSync()
                return
            }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(100)
        }
    }

    private companion object {
        const val PREFS = "phone_core_setup_wizard_v2"
        const val KEY_ATTEMPTED_TARGET = "attempted_target"
        const val KEY_COMPLETED = "completed"
    }
}
