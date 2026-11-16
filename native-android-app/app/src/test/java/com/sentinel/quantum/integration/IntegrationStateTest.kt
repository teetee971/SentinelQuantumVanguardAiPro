package com.sentinel.quantum.integration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class IntegrationStateTest {
    @Test
    fun productStatesAreExactlyTheApprovedFiveValues() {
        assertEquals(
            listOf(
                "CONNECTED",
                "LOCKED_EXTERNAL_DEPENDENCY",
                "PHYSICAL_REQUIRED",
                "INTENTIONALLY_DISABLED",
                "CI_ONLY"
            ),
            IntegrationState.entries.map { it.name }
        )
    }

    @Test
    fun unknownCapabilityIdIsRejectedFailClosed() {
        assertNull(IntegrationCapabilityId.fromWireName("unknown_capability"))
    }

    @Test
    fun nonConnectedStatusRequiresDiagnosticReason() {
        assertThrows(IllegalArgumentException::class.java) {
            IntegrationStatus(IntegrationState.PHYSICAL_REQUIRED, null)
        }
    }

    @Test
    fun connectedStatusCannotCarryADegradedDiagnosticReason() {
        assertThrows(IllegalArgumentException::class.java) {
            IntegrationStatus(
                IntegrationState.CONNECTED,
                IntegrationDiagnosticReason.PHYSICAL_VALIDATION_REQUIRED
            )
        }
    }

    @Test
    fun stableCapabilityIdsCoverCanonicalRegistryFamilies() {
        assertEquals(25, IntegrationCapabilityId.entries.size)
        assertEquals(
            IntegrationCapabilityId.CALL_SCREENING,
            IntegrationCapabilityId.fromWireName("call_screening")
        )
        assertEquals(
            IntegrationCapabilityId.SYSTEM_DOCTOR_RELEASE_EVIDENCE,
            IntegrationCapabilityId.fromWireName("system_doctor_release_evidence")
        )
    }
}
