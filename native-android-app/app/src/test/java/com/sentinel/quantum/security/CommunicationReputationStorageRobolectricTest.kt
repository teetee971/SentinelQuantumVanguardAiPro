package com.sentinel.quantum.security

import android.content.Context
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class CommunicationReputationStorageRobolectricTest : CommunicationReputationStorageCases() {
    override fun applicationContextForTest(): Context = RuntimeEnvironment.getApplication()
}
