package com.sentinel.quantum.ui

import android.app.Activity
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sentinel.quantum.PhoneCoreActivationActivity
import com.sentinel.quantum.PhoneCoreDiagnosticActivity
import com.sentinel.quantum.VoiceStudioActivity
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/** Smoke coverage for non-exported application surfaces that are not destinations of NavGraph. */
@RunWith(AndroidJUnit4::class)
class StandaloneActivitySmokeInstrumentationTest {
    @Test fun phoneCoreActivationRenders() = launchAndResume(PhoneCoreActivationActivity::class.java)
    @Test fun phoneCoreDiagnosticRenders() = launchAndResume(PhoneCoreDiagnosticActivity::class.java)
    @Test fun voiceStudioRenders() = launchAndResume(VoiceStudioActivity::class.java)

    private fun <T : Activity> launchAndResume(activityClass: Class<T>) {
        val scenario = ActivityScenario.launch(activityClass)
        try {
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                assertFalse("${activityClass.simpleName} finished during smoke launch", activity.isFinishing)
            }
        } finally {
            scenario.close()
        }
    }
}
