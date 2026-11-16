package com.sentinel.quantum.integration

import org.junit.Assert.*
import org.junit.Test

class IntegrationRegistryClockRollbackTest {
    private fun connected(at: Long) = IntegrationObservation(
        IntegrationCapabilityId.VPN_RUNTIME, IntegrationStatus(IntegrationState.CONNECTED), at
    )
    @Test fun expiredEvidenceCannotBecomeFreshAgainAfterClockRollback() {
        val r = IntegrationRegistry(emptyMap(), 30_000L)
        assertTrue(r.publish(connected(1000)))
        assertEquals(IntegrationState.CONNECTED, r.snapshot(1000).status(IntegrationCapabilityId.VPN_RUNTIME).state)
        assertEquals(IntegrationState.LOCKED_EXTERNAL_DEPENDENCY, r.snapshot(31_001).status(IntegrationCapabilityId.VPN_RUNTIME).state)
        assertEquals(IntegrationState.LOCKED_EXTERNAL_DEPENDENCY, r.snapshot(2000).status(IntegrationCapabilityId.VPN_RUNTIME).state)
    }
    @Test fun rollbackInvalidatesAllCapabilitiesAndCannotBeRepairedByPublishing() {
        val r = IntegrationRegistry(emptyMap())
        assertTrue(r.publish(connected(1000)))
        r.snapshot(2000)
        r.snapshot(1999)
        assertFalse(r.publish(connected(3000)))
        assertEquals(IntegrationState.LOCKED_EXTERNAL_DEPENDENCY, r.snapshot(3000).status(IntegrationCapabilityId.VPN_RUNTIME).state)
    }
    @Test fun invalidTimeCannotFallBackToAnOptimisticBaseline() {
        val r = IntegrationRegistry(mapOf(IntegrationCapabilityId.VPN_RUNTIME to IntegrationStatus(IntegrationState.CONNECTED)))
        assertEquals(IntegrationState.LOCKED_EXTERNAL_DEPENDENCY, r.snapshot(-1).status(IntegrationCapabilityId.VPN_RUNTIME).state)
        assertFalse(r.publish(connected(1000)))
    }
    @Test fun freshRegistryNeedsFreshEvidenceAfterInvalidation() {
        val old = IntegrationRegistry(emptyMap()); old.snapshot(-1)
        val fresh = IntegrationRegistry(emptyMap())
        assertEquals(IntegrationState.LOCKED_EXTERNAL_DEPENDENCY, fresh.snapshot(1000).status(IntegrationCapabilityId.VPN_RUNTIME).state)
        assertTrue(fresh.publish(connected(1000)))
        assertEquals(IntegrationState.CONNECTED, fresh.snapshot(1000).status(IntegrationCapabilityId.VPN_RUNTIME).state)
    }
}
