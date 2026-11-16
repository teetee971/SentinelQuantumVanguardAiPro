package com.sentinel.quantum.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure JVM coverage of the interval allowlist used to schedule the OSINT watch. */
class SettingsStoreTest {

    @Test
    fun supportedIntervalsArePreserved() {
        SettingsStore.SUPPORTED_INTERVALS_HOURS.forEach { hours ->
            assertEquals(hours, SettingsStore.sanitizeInterval(hours))
        }
    }

    @Test
    fun supportedIntervalsAreTheDocumentedOnes() {
        assertEquals(listOf(0, 4, 12, 24), SettingsStore.SUPPORTED_INTERVALS_HOURS)
    }

    @Test
    fun unsupportedIntervalsFallBackToNever() {
        listOf(-24, -1, 1, 3, 5, 13, 23, 48, Int.MAX_VALUE, Int.MIN_VALUE).forEach { hours ->
            assertEquals(SettingsStore.INTERVAL_NEVER, SettingsStore.sanitizeInterval(hours))
        }
    }
}
