package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneMonitorTest {
    private fun monitor(logs: MutableList<String> = mutableListOf(), time: Long = 42L) =
        PhoneMonitor({ _, _, message -> logs += message }, { time })

    @Test fun knownPrefixIsElevated() {
        val result = monitor().checkNumber("+33 8 99 12 34 56")
        assertEquals(PhoneMonitor.RiskLevel.HIGH, result.riskLevel)
        assertTrue(result.validFormat)
    }

    @Test fun ordinaryNumberIsNormalized() {
        val result = monitor().checkNumber("+33 6 12 34 56 78")
        assertEquals(PhoneMonitor.RiskLevel.LOW, result.riskLevel)
        assertEquals("+33612345678", result.phoneNumber)
    }

    @Test fun malformedInputFailsClosed() {
        val result = monitor().checkNumber("+33<script>")
        assertEquals(PhoneMonitor.RiskLevel.MEDIUM, result.riskLevel)
        assertFalse(result.validFormat)
    }

    @Test fun statisticsReflectChecks() {
        val monitor = monitor()
        monitor.checkNumber("+33 6 12 34 56 78")
        monitor.checkNumber("0899 12 34 56")
        assertEquals(PhoneMonitor.MonitorStats(2, 1, 42L), monitor.getStats())
    }

    @Test fun auditDoesNotContainFullNumber() {
        val logs = mutableListOf<String>()
        monitor(logs).checkNumber("+33 6 12 34 56 78")
        assertTrue(logs.none { it.contains("+33612345678") })
    }
}
