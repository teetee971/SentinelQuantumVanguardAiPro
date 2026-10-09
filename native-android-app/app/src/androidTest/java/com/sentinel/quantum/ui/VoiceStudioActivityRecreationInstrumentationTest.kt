package com.sentinel.quantum.ui

import android.app.Activity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sentinel.quantum.VoiceStudioActivity
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VoiceStudioActivityRecreationInstrumentationTest {
    @Test
    fun localPreviewSurvivesConfigurationRecreation() {
        val scenario = ActivityScenario.launch(VoiceStudioActivity::class.java)
        try {
            scenario.onActivity { activity ->
                previewFile(activity).writeBytes(byteArrayOf(1, 2, 3))
            }

            scenario.recreate()

            scenario.onActivity { activity ->
                assertTrue(
                    "La rotation ne doit pas supprimer un aperçu vocal local valide",
                    previewFile(activity).exists()
                )
            }
        } finally {
            scenario.close()
        }
    }

    private fun previewFile(activity: Activity): File =
        File(activity.cacheDir, "voice-studio-preview.m4a")
}
