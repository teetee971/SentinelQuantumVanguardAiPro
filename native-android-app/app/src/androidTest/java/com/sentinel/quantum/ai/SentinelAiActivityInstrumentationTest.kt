package com.sentinel.quantum.ai

import android.content.ComponentName
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Entry and process revocation checks. The held effect is an in-memory fixture. */
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

    @Test fun processRevocationBlocksBothActivitiesUntilDrainedAndRequiresNewConsent() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue("Use the isolated validation package for a test that appends a revocation event",
            context.packageName == SENTINEL_AI_C11_PACKAGE)

        val activityClass = SentinelAiActivity::class.java
        val gate = activityClass.getDeclaredField("processGate").apply { isAccessible = true }
            .get(null) as SentinelAiProcessGate
        val leaseField = activityClass.getDeclaredField("consentLease").apply { isAccessible = true }
        val second = AtomicReference<SentinelAiActivity>()
        val secondResumed = CountDownLatch(1)
        val monitor = ActivityLifecycleMonitorRegistry.getInstance()
        val effectEntered = CountDownLatch(1)
        val releaseEffect = CountDownLatch(1)
        val effects = Executors.newSingleThreadExecutor()

        ActivityScenario.launch(activityClass).use { scenario ->
            var observer: ActivityLifecycleCallback? = null
            try {
                scenario.onActivity { first ->
                    val callback = ActivityLifecycleCallback { activity, stage ->
                        if (stage == Stage.RESUMED && activity is SentinelAiActivity && activity !== first &&
                            second.compareAndSet(null, activity)
                        ) secondResumed.countDown()
                    }
                    observer = callback
                    monitor.addLifecycleCallback(callback)
                    // Launch from the first activity to preserve it in the same task.
                    first.startActivity(Intent(first, activityClass))
                }
                assertTrue("A second instance must resume", secondResumed.await(10, TimeUnit.SECONDS))
                val affected = checkNotNull(second.get())
                lateinit var old: SentinelAiProcessGate.Lease
                scenario.onActivity { first ->
                    assertNotSame(first, affected)
                    assertFalse(first.isDestroyed)
                    val consent = affected.tagged<CheckBox>("sentinel_ai_consent")
                    assertTrue(consent.isEnabled)
                    consent.performClick()
                    assertTrue(consent.isChecked)
                    old = leaseField.get(affected) as SentinelAiProcessGate.Lease
                    assertTrue(gate.isActive(old))
                }

                val effect = effects.submit {
                    gate.execute(old) {
                        effectEntered.countDown()
                        check(releaseEffect.await(20, TimeUnit.SECONDS)) { "Fixture effect release timed out" }
                    }
                }
                assertTrue("The old effect must be admitted", effectEntered.await(5, TimeUnit.SECONDS))
                scenario.onActivity { first ->
                    // Dispatch the first activity's handler while the second owns the live lease.
                    assertTrue(first.tagged<Button>("sentinel_ai_revoke_all").performClick())
                    assertFalse("The old lease must be invalid before the click returns", gate.isActive(old))
                    assertFalse("The effect remains held", effect.isDone)
                    assertThrows(IllegalStateException::class.java) { gate.capture() }
                    assertEquals("Révocation des autorisations IA du processus demandée…",
                        affected.tagged<TextView>("sentinel_ai_status").text.toString())
                    for (activity in listOf(first, affected)) {
                        val consent = activity.tagged<CheckBox>("sentinel_ai_consent")
                        assertFalse(consent.isEnabled)
                        assertFalse(consent.isChecked)
                        // Simulate a stale checked callback despite the disabled widget.
                        consent.isChecked = true
                        assertFalse(consent.isChecked)
                        assertNull(leaseField.get(activity))
                        assertFalse(activity.tagged<Button>("sentinel_ai_send").isEnabled)
                    }
                }

                releaseEffect.countDown()
                effect.get(5, TimeUnit.SECONDS)
                val deadline = SystemClock.uptimeMillis() + 10_000
                var confirmed = false
                while (!confirmed && SystemClock.uptimeMillis() < deadline) {
                    scenario.onActivity {
                        confirmed = affected.tagged<TextView>("sentinel_ai_status").text
                            .startsWith("Autorisations IA révoquées dans ce processus")
                    }
                    if (!confirmed) SystemClock.sleep(20)
                }
                assertTrue("Revocation must acknowledge the drained effect", confirmed)
                scenario.onActivity { first ->
                    for (activity in listOf(first, affected)) {
                        val consent = activity.tagged<CheckBox>("sentinel_ai_consent")
                        assertTrue(consent.isEnabled)
                        assertFalse(consent.isChecked)
                        assertNull(leaseField.get(activity))
                        assertFalse(activity.tagged<Button>("sentinel_ai_send").isEnabled)
                    }
                    val consent = affected.tagged<CheckBox>("sentinel_ai_consent")
                    consent.performClick()
                    assertTrue(consent.isChecked)
                    val fresh = leaseField.get(affected) as SentinelAiProcessGate.Lease
                    assertTrue(gate.isActive(fresh))
                    assertFalse(gate.isActive(old))
                    assertNull(leaseField.get(first))
                }
            } finally {
                releaseEffect.countDown()
                effects.shutdown()
                instrumentation.runOnMainSync {
                    observer?.let(monitor::removeLifecycleCallback)
                    second.get()?.finish()
                }
            }
        }
    }

    private fun <T : View> SentinelAiActivity.tagged(tag: String): T =
        findViewById<View>(android.R.id.content).findViewWithTag(tag)
}
