package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WifiScanResultTruthTest {
    @Test fun freshNonEmptyResultsNeedNoWarning() {
        assertNull(WifiScanResultTruth.statusMessage(WifiScanResultTruth.Source.FRESH, 3))
    }

    @Test fun freshEmptyResultsAreDistinguishedFromScanFailure() {
        assertEquals(
            "Scan actualisé : aucun réseau visible.",
            WifiScanResultTruth.statusMessage(WifiScanResultTruth.Source.FRESH, 0)
        )
    }

    @Test fun cachedResultsAlwaysExplainWhyTheyMayBeOld() {
        val sources = listOf(
            WifiScanResultTruth.Source.CACHED_SCAN_REJECTED,
            WifiScanResultTruth.Source.CACHED_PLATFORM_STALE,
            WifiScanResultTruth.Source.CACHED_TIMEOUT
        )
        sources.forEach { source ->
            val message = WifiScanResultTruth.statusMessage(source, 2)
            requireNotNull(message)
            assert(message.contains("derniers résultats"))
        }
    }
}
