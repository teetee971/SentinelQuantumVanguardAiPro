package com.sentinel.quantum

import android.content.Context
import android.content.Intent
import androidx.lifecycle.Lifecycle
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
 * Seeds an interrupted first-run session for the following workflow step to reboot the AVD.
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
        if (!preserveState) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test
    fun leavesInterruptedSetupForRebootQualification() {
        val wizard = PhoneCoreSetupWizardStore(context)
        val target = PhoneCoreSetupWizardStore.targetKey(PhoneCoreSetupWizardStore.Step.DIALER_ROLE)
        wizard.markInProgress()
        wizard.markAttemptedTarget(target)

        val scenario = ActivityScenario.launch<PhoneCoreActivationActivity>(
            Intent(context, PhoneCoreActivationActivity::class.java)
                .putExtra(PhoneCoreActivationActivity.EXTRA_FIRST_RUN_SETUP, true)
        )
        try {
            scenario.moveToState(Lifecycle.State.RESUMED)
            instrumentation.waitForIdleSync()
        } finally {
            scenario.close()
        }

        instrumentation.uiAutomation.executeShellCommand("am force-stop ${context.packageName}").use { }
        val persisted = PhoneCoreSetupWizardStore(context)
        assertEquals(PhoneCoreSetupWizardStore.LifecycleState.IN_PROGRESS, persisted.lifecycleState())
        assertNotNull(persisted.attemptedTargetKey())
        check(!persisted.isCompleted()) { "reboot preparation must never manufacture COMPLETED" }
    }

    private companion object {
        const val PREFS = "phone_core_setup_wizard_v2"
    }
}
