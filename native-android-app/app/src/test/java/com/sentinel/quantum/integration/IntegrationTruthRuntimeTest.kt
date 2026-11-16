package com.sentinel.quantum.integration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class IntegrationTruthRuntimeTest {
    @Before
    fun reset() {
        IntegrationTruthRuntime.clearForTests()
    }

    @Test
    fun freshAcceptedRuntimeEvidenceTemporarilyProvesConnected() {
        assertTrue(
            IntegrationTruthRuntime.publishConnected(
                IntegrationCapabilityId.SMS,
                observedAtMs = 1_000L
            )
        )

        assertEquals(
            IntegrationState.CONNECTED,
            IntegrationTruthRuntime.snapshot(nowMs = 30_000L)
                .status(IntegrationCapabilityId.SMS).state
        )
        assertEquals(
            IntegrationState.LOCKED_EXTERNAL_DEPENDENCY,
            IntegrationTruthRuntime.snapshot(nowMs = 31_001L)
                .status(IntegrationCapabilityId.SMS).state
        )
    }

    @Test
    fun olderRuntimeEvidenceCannotReplaceNewerEvidence() {
        assertTrue(
            IntegrationTruthRuntime.publishConnected(
                IntegrationCapabilityId.MMS,
                observedAtMs = 10_000L
            )
        )
        assertFalse(
            IntegrationTruthRuntime.publish(
                capabilityId = IntegrationCapabilityId.MMS,
                status = IntegrationStatus(
                    IntegrationState.PHYSICAL_REQUIRED,
                    IntegrationDiagnosticReason.PHYSICAL_VALIDATION_REQUIRED
                ),
                observedAtMs = 9_999L
            )
        )
    }

    @Test
    fun missingRuntimeEvidenceFailsClosed() {
        val status = IntegrationTruthRuntime.snapshot(nowMs = 1_000L)
            .status(IntegrationCapabilityId.WEARABLE_RUNTIME)
        assertEquals(IntegrationState.LOCKED_EXTERNAL_DEPENDENCY, status.state)
        assertEquals(IntegrationDiagnosticReason.RUNTIME_NOT_OBSERVED, status.diagnosticReason)
    }
}
