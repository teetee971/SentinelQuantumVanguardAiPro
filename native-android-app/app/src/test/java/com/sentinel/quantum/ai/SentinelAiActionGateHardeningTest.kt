package com.sentinel.quantum.ai

import org.junit.Assert.*
import org.junit.Test

class SentinelAiActionGateHardeningTest {

    @Test
    fun timestampBeforeCreationMustBeRejected() {
        val gate = SentinelAiActionGate()
        val request = gate.request(
            "change_setting", "enabled=true", 1000L
        )

        assertThrows(IllegalStateException::class.java) {
            gate.confirm(request.token, "change_setting", "enabled=true", 999L)
        }
        assertEquals(0, gate.pendingCount())
    }

    @Test
    fun pendingRequestsMustHaveABound() {
        val gate = SentinelAiActionGate()
        var rejected = false

        repeat(10001) {
            try {
                gate.request("action", "value", 1000L)
            } catch (_: IllegalStateException) {
                rejected = true
                return@repeat
            }
        }

        assertTrue(
            "Unbounded pending requests",
            rejected
        )
    }
}
