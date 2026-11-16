package com.sentinel.quantum.ai

import android.content.ComponentName
import android.widget.Button
import android.widget.CheckBox
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Entry contract only. Does not simulate or claim successful human authentication or inference. */
@RunWith(AndroidJUnit4::class)
class SentinelAiActivityInstrumentationTest {
    @Test fun entryRequiresConsentAndDoesNotInitializeJournal() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val events = File(context.noBackupFilesDir, "sentinel-ai-audit-v1/events")
        val before = if (events.exists()) events.readBytes() else null
        ActivityScenario.launch(SentinelAiActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertFalse(activity.findViewById<android.view.View>(android.R.id.content)
                    .findViewWithTag<CheckBox>("sentinel_ai_consent").isChecked)
                assertFalse(activity.findViewById<android.view.View>(android.R.id.content)
                    .findViewWithTag<Button>("sentinel_ai_send").isEnabled)
                val info = context.packageManager.getActivityInfo(ComponentName(context, SentinelAiActivity::class.java), 0)
                assertFalse(info.exported)
            }
        }
        val after = if (events.exists()) events.readBytes() else null
        assertArrayEquals(before, after)
    }
}
