package com.sentinel.quantum

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SentinelApplicationColdStartBoundaryTest {
    @Test
    fun application_onCreate_does_not_start_non_critical_screening_work() {
        val source = source("src/main/java/com/sentinel/quantum/SentinelApplication.kt")
        val onCreate = source.substringAfter("override fun onCreate()")
            .substringBefore("\n    }")

        listOf(
            "queueProviderRepair(",
            "scheduleStartupRecovery(",
            "CallBlocklistStore(",
            "prepareScreeningSnapshot(",
            "prepareFingerprintKeys("
        ).forEach { forbidden ->
            assertFalse(
                "Application.onCreate must not invoke $forbidden before CallScreeningService",
                onCreate.contains(forbidden)
            )
        }
    }

    @Test
    fun maintenance_is_not_started_by_main_activity() {
        val activitySource = source("src/main/java/com/sentinel/quantum/MainActivity.kt")

        assertFalse(activitySource.contains("runNonCriticalStartupMaintenance()"))
        assertFalse(activitySource.contains("runPostResponseMaintenance()"))
    }

    @Test
    fun foreground_resume_keeps_sms_mms_maintenance_alive() {
        val activitySource = source("src/main/java/com/sentinel/quantum/MainActivity.kt")
        val applicationSource = source("src/main/java/com/sentinel/quantum/SentinelApplication.kt")

        assertTrue(activitySource.contains("override fun onPostResume()"))
        assertTrue(activitySource.contains("launchForegroundMaintenance()"))
        assertTrue(applicationSource.contains("fun launchForegroundMaintenance()"))
    }

    @Test
    fun maintenance_is_owned_by_post_response_screening_work() {
        val serviceSource = source(
            "src/main/java/com/sentinel/quantum/security/SentinelCallScreeningService.kt"
        )
        val responseIndex = serviceSource.indexOf("respondToCall(callDetails, response.build())")
        val submissionIndex = serviceSource.indexOf(
            "responseBoundary.submitPostResponse",
            responseIndex
        )
        val maintenanceIndex = serviceSource.indexOf("runPostResponseMaintenance()", submissionIndex)

        assertTrue("screening response must exist", responseIndex >= 0)
        assertTrue("post-response submission must follow the response", submissionIndex > responseIndex)
        assertTrue(
            "maintenance must run in the post-response worker",
            maintenanceIndex > submissionIndex
        )
    }

    private fun source(path: String): String {
        val candidates = listOf(
            File(path),
            File("app/$path"),
            File("native-android-app/app/$path")
        )
        return candidates.firstOrNull(File::isFile)?.readText()
            ?: error("Source not found from ${File(".").absolutePath}: $path")
    }
}
