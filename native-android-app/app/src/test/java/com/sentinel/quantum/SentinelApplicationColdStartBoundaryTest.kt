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
    fun non_critical_maintenance_is_started_from_resumed_activity_on_io() {
        val activitySource = source("src/main/java/com/sentinel/quantum/MainActivity.kt")
        val applicationSource = source("src/main/java/com/sentinel/quantum/SentinelApplication.kt")

        assertTrue(activitySource.contains("repeatOnLifecycle(Lifecycle.State.RESUMED)"))
        assertTrue(applicationSource.contains("withContext(Dispatchers.IO)"))
        assertTrue(activitySource.contains("runNonCriticalStartupMaintenance()"))
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
