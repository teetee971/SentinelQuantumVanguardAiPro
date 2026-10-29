package com.sentinel.quantum.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.sentinel.quantum.MmsAttachmentDraftViewModel
import com.sentinel.quantum.SmsComposeActivity
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

/** Test-only PNG in the existing app cache provider; no SMS/contact provider write or sending. */
class MmsAttachmentRecreationInstrumentationTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test fun validatedImageSurvivesActivityRecreation() = withSelectedImage { scenario ->
        scenario.recreate()
        rule.waitForIdle()
        rule.onNodeWithTag("phone_core_mms_attachment_count").performScrollTo().assertTextContains("1 /")
    }

    @Test fun removedImageDoesNotReturnAfterActivityRecreation() = withSelectedImage { scenario ->
        rule.onNodeWithText("Retirer").performScrollTo().performClick()
        scenario.recreate()
        rule.waitForIdle()
        rule.onNodeWithTag("phone_core_mms_attachment_count").performScrollTo().assertTextContains("0 /")
    }

    private fun withSelectedImage(check: (ActivityScenario<SmsComposeActivity>) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "sentinel_mms_send").apply { mkdirs() }
        val file = File(directory, "d1-recreation-${UUID.randomUUID()}.png")
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.CYAN)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mmsto:"))
                .setClass(context, SmsComposeActivity::class.java)
            ActivityScenario.launch<SmsComposeActivity>(intent).use { scenario ->
                lateinit var model: MmsAttachmentDraftViewModel
                scenario.onActivity { activity ->
                    model = ViewModelProvider(activity)[MmsAttachmentDraftViewModel::class.java]
                    model.draft.select(listOf(uri.toString()))
                }
                rule.waitUntil(timeoutMillis = 5_000) {
                    !model.draft.state.value.loading && model.draft.state.value.attachments.size == 1
                }
                rule.onNodeWithTag("phone_core_mms_attachment_count").performScrollTo().assertTextContains("1 /")
                check(scenario)
            }
        } finally {
            bitmap.recycle()
            file.delete() // Only this test-owned unique file, never the cache directory or a user PDU.
        }
    }
}
