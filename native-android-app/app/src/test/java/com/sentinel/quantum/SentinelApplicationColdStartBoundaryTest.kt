package com.sentinel.quantum

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SentinelApplicationColdStartBoundaryTest {
    @Test
    fun `application cold start keeps only screening critical preload synchronous`() {
        val applicationSource = File(
            "src/main/java/com/sentinel/quantum/SentinelApplication.kt"
        ).readText()
        val onCreate = applicationSource.substringAfter("override fun onCreate()")

        assertTrue(onCreate.contains("store.prepareScreeningSnapshot()"))
        assertTrue(onCreate.contains("store.prepareFingerprintKeys()"))
        assertFalse(onCreate.contains("SentinelSmsStatusReceiver.queueProviderRepair"))
        assertFalse(onCreate.contains("MmsSendCleanupWorker.scheduleStartupRecovery"))
    }

    @Test
    fun `sms and mms startup maintenance begins only from resumed foreground on io`() {
        val applicationSource = File(
            "src/main/java/com/sentinel/quantum/SentinelApplication.kt"
        ).readText()
        val activitySource = File(
            "src/main/java/com/sentinel/quantum/MainActivity.kt"
        ).readText()

        assertTrue(applicationSource.contains("maintenanceScope"))
        assertTrue(applicationSource.contains("Dispatchers.IO"))
        assertTrue(applicationSource.contains("fun launchForegroundMaintenance()"))
        assertTrue(applicationSource.contains("SentinelSmsStatusReceiver.queueProviderRepair(applicationContext)"))
        assertTrue(applicationSource.contains("MmsSendCleanupWorker.scheduleStartupRecovery(applicationContext)"))

        val onPostResume = activitySource.substringAfter("override fun onPostResume()")
            .substringBefore("override fun", missingDelimiterValue = activitySource.substringAfter("override fun onPostResume()"))
        assertTrue(onPostResume.contains("launchForegroundMaintenance()"))
    }
}
