package com.sentinel.quantum.ai

import org.junit.Assert.*
import org.junit.Test

class SentinelAiV3ConfigTest {

    @Test
    fun defaultsMatchApprovedConfiguration() {
        val config = SentinelAiV3Config()

        assertEquals(AiEngineMode.AUTO, config.engine)
        assertEquals(AiModelMode.AUTO, config.model)
        assertEquals(AiVisualMode.QUANTUM_ORB, config.visual)
        assertTrue(config.voiceResponsesEnabled)
        assertTrue(config.sensitiveActionConfirmationEnabled)
    }

    @Test(expected = IllegalArgumentException::class)
    fun sensitiveActionConfirmationCannotBeDisabled() {
        SentinelAiV3Config(
            sensitiveActionConfirmationEnabled = false
        )
    }

    @Test
    fun voiceResponsesCanBeDisabledIndependently() {
        val config = SentinelAiV3Config(
            voiceResponsesEnabled = false
        )

        assertFalse(config.voiceResponsesEnabled)
        assertTrue(config.sensitiveActionConfirmationEnabled)
    }
}
