package com.sentinel.quantum.integration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntegrationRegistryTest {
    private val baseline = mapOf(
        IntegrationCapabilityId.VPN_RUNTIME to IntegrationStatus(
            IntegrationState.LOCKED_EXTERNAL_DEPENDENCY,
            IntegrationDiagnosticReason.GATEWAY_NOT_AVAILABLE
        )
    )

    @Test
    fun freshObservationMayOverrideBaselineButStaleObservationCannotPreserveConnected() {
        val registry = IntegrationRegistry(baseline, observationTtlMs = 30_000L)
        assertTrue(
            registry.publish(
                IntegrationObservation(
                    IntegrationCapabilityId.VPN_RUNTIME,
                    IntegrationStatus(IntegrationState.CONNECTED),
                    observedAtMs = 1_000L
                )
            )
        )

        assertEquals(
            IntegrationState.CONNECTED,
            registry.snapshot(nowMs = 30_000L).status(IntegrationCapabilityId.VPN_RUNTIME).state
        )
        assertEquals(
            IntegrationState.LOCKED_EXTERNAL_DEPENDENCY,
            registry.snapshot(nowMs = 31_001L).status(IntegrationCapabilityId.VPN_RUNTIME).state
        )
    }

    @Test
    fun identicalDuplicateIsIdempotentAndConflictingSameTimestampIsRejected() {
        val registry = IntegrationRegistry(baseline)
        val first = IntegrationObservation(
            IntegrationCapabilityId.VPN_RUNTIME,
            IntegrationStatus(IntegrationState.CONNECTED),
            observedAtMs = 5_000L
        )
        assertTrue(registry.publish(first))
        assertTrue(registry.publish(first))
        assertFalse(
            registry.publish(
                first.copy(
                    status = IntegrationStatus(
                        IntegrationState.PHYSICAL_REQUIRED,
                        IntegrationDiagnosticReason.PHYSICAL_VALIDATION_REQUIRED
                    )
                )
            )
        )
    }

    @Test
    fun olderObservationCannotReplaceNewerEvidence() {
        val registry = IntegrationRegistry(baseline)
        assertTrue(
            registry.publish(
                IntegrationObservation(
                    IntegrationCapabilityId.VPN_RUNTIME,
                    IntegrationStatus(IntegrationState.CONNECTED),
                    observedAtMs = 10_000L
                )
            )
        )
        assertFalse(
            registry.publish(
                IntegrationObservation(
                    IntegrationCapabilityId.VPN_RUNTIME,
                    IntegrationStatus(
                        IntegrationState.LOCKED_EXTERNAL_DEPENDENCY,
                        IntegrationDiagnosticReason.RUNTIME_NOT_OBSERVED
                    ),
                    observedAtMs = 9_999L
                )
            )
        )
    }

    @Test
    fun missingBaselineAndMissingEvidenceFailClosed() {
        val registry = IntegrationRegistry(emptyMap())
        val status = registry.snapshot(nowMs = 1_000L).status(IntegrationCapabilityId.MESH_RUNTIME)
        assertEquals(IntegrationState.LOCKED_EXTERNAL_DEPENDENCY, status.state)
        assertEquals(IntegrationDiagnosticReason.RUNTIME_NOT_OBSERVED, status.diagnosticReason)
    }

    @Test
    fun futureObservationCannotProveCurrentConnectedState() {
        val registry = IntegrationRegistry(baseline)
        assertTrue(
            registry.publish(
                IntegrationObservation(
                    IntegrationCapabilityId.VPN_RUNTIME,
                    IntegrationStatus(IntegrationState.CONNECTED),
                    observedAtMs = 2_000L
                )
            )
        )
        assertEquals(
            IntegrationState.LOCKED_EXTERNAL_DEPENDENCY,
            registry.snapshot(nowMs = 1_999L).status(IntegrationCapabilityId.VPN_RUNTIME).state
        )
    }
}
