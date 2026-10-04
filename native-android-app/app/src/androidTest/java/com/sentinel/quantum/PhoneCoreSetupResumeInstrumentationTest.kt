package com.sentinel.quantum

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.KeyEvent
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
        runCatching { instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK) }
        prefs.edit().clear().commit()
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

            // The first missing prerequisite may have opened an Android role/permission surface.
            // Dismiss that system-owned surface exactly as an interrupted user flow would.
            SystemClock.sleep(750)
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            instrumentation.waitForIdleSync()
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

    private companion object {
        const val PREFS = "phone_core_setup_wizard_v2"
        const val KEY_ATTEMPTED_TARGET = "attempted_target"
        const val KEY_COMPLETED = "completed"
    }
}
