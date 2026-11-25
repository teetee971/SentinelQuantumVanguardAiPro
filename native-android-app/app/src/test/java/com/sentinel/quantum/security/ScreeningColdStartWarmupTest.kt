package com.sentinel.quantum.security

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreeningColdStartWarmupTest {
    @Test fun callbackWaitsForABoundedWarmupBeforeReadingRules() {
        val service = source("security/SentinelCallScreeningService.kt")
        val callback = service.substring(service.indexOf("override fun onScreenCall"))
        val response = callback.indexOf("respondToCall(callDetails, response.build())")
        val preResponse = callback.substring(0, response)
        val await = preResponse.indexOf("ScreeningProcessWarmup.awaitForScreening(")
        val snapshot = preResponse.indexOf("CallBlocklistStore.cachedSnapshotForScreening()")
        assertTrue(await >= 0 && snapshot > await)
        assertFalse(preResponse.contains("getSharedPreferences"))
        assertFalse(preResponse.contains("KeyStore.getInstance"))
        assertFalse(preResponse.contains("CallBlocklistStore("))
        assertTrue(ScreeningProcessWarmup.CALLBACK_WAIT_BUDGET_MS in 1L..1_000L)
    }

    @Test fun slowWarmupCannotHoldTheCallbackUpToTheFrameworkTimeout() {
        ScreeningProcessWarmup.resetForTests()
        ScreeningProcessWarmup.scheduleLoader { Thread.sleep(5_000) }
        val started = System.nanoTime()
        val ready = ScreeningProcessWarmup.awaitForScreening(80)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        ScreeningProcessWarmup.resetForTests()
        assertFalse(ready)
        assertTrue("elapsedMs=$elapsedMs", elapsedMs < 400)
    }

    @Test fun warmupLoadsRulesBeforeExactMatchKeysAndStaysOffTheCallback() {
        val warmup = source("security/ScreeningProcessWarmup.kt")
        val rules = warmup.indexOf("prepareScreeningSnapshot(")
        val keys = warmup.indexOf("prepareFingerprintKeys(")
        assertTrue(rules >= 0 && keys > rules)
        assertFalse(warmup.contains("KeyGenerator.getInstance"))
        val service = source("security/SentinelCallScreeningService.kt")
        val callback = service.substring(
            service.indexOf("override fun onScreenCall"),
            service.indexOf("respondToCall(callDetails, response.build())")
        )
        assertFalse(callback.contains("prepareFingerprintKeys("))
        assertFalse(callback.contains("prepareScreeningSnapshot("))
    }

    private fun source(path: String): String {
        val candidates = listOf(
            File("src/main/java/com/sentinel/quantum/$path"),
            File("app/src/main/java/com/sentinel/quantum/$path"),
            File("native-android-app/app/src/main/java/com/sentinel/quantum/$path")
        )
        return candidates.firstOrNull(File::isFile)?.readText()
            ?: error("missing $path")
    }
}
