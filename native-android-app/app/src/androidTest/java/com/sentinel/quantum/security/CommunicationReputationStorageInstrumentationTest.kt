package com.sentinel.quantum.security

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.runner.RunWith

/** All preferences, including nested SettingsStore and HMAC secret, are test-namespaced. */
@RunWith(AndroidJUnit4::class)
class CommunicationReputationStorageInstrumentationTest : CommunicationReputationStorageCases() {
    override fun applicationContextForTest(): Context =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
}
