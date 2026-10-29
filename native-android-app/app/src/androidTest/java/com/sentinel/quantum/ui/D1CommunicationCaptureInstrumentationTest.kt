package com.sentinel.quantum.ui

import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.sentinel.quantum.SentinelDialerActivity
import com.sentinel.quantum.SmsComposeActivity
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Opt-in actual runtime captures, never a rendering of invented SMS/contact data. */
class D1CommunicationCaptureInstrumentationTest {
    @Test fun phoneCapture() = capture("01-phone", SentinelDialerActivity::class.java)
    @Test fun contactsCapture() = capture("02-contacts", SentinelDialerActivity::class.java, contacts = true)
    @Test fun messagesCapture() = capture("03-messages", SmsComposeActivity::class.java)

    private fun <T : ComponentActivity> capture(name: String, type: Class<T>, contacts: Boolean = false) {
        assumeTrue("Enable only on an empty test runtime; screenshots may contain private data",
            InstrumentationRegistry.getArguments().getString("captureD1Screenshots") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val intent = Intent(context, type)
        if (contacts) intent.putExtra(SentinelDialerActivity.EXTRA_OPEN_CONTACTS, true)
        ActivityScenario.launch<T>(intent).use { scenario ->
            instrumentation.waitForIdleSync()
            scenario.onActivity { assertTrue(it.window.decorView.isShown) }
            val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            val dir = File(context.getExternalFilesDir(null), "d1-captures").apply { mkdirs() }
            File(dir, "$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
        }
    }
}
