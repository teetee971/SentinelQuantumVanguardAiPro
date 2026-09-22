package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneCoreDiagnosticsTest {
    @Test fun doesNotClaimReadyWithoutRuntimePrerequisites() {
        val r = PhoneCoreDiagnostics.readiness(PhoneCoreDiagnostics.RuntimeFacts(false,false,false,false,false,false,false,false,false,false,false))
        assertEquals(PhoneCoreDiagnostics.State.LOCKED, r.capabilities.first { it.id == "DIALER" }.state)
        assertEquals(PhoneCoreDiagnostics.State.LOCKED, r.capabilities.first { it.id == "MMS_ATTACHMENTS" }.state)
        assertEquals(PhoneCoreDiagnostics.State.LIMITED, r.capabilities.first { it.id == "PHYSICAL_DEVICE" }.state)
        assertFalse(r.softwarePrerequisitesReady)
        assertFalse(r.fullyValidated)
    }

    @Test fun softwareReadyDoesNotClaimPhysicalValidation() {
        val r = PhoneCoreDiagnostics.readiness(PhoneCoreDiagnostics.RuntimeFacts(true,true,true,true,true,true,true,true,true,true,false))
        assertTrue(r.softwarePrerequisitesReady)
        assertFalse(r.physicalDeviceValidated)
        assertFalse(r.fullyValidated)
        assertEquals(PhoneCoreDiagnostics.State.LIMITED, r.capabilities.first { it.id == "PHYSICAL_DEVICE" }.state)
    }

    @Test fun fullValidationRequiresSoftwareAndPhysicalDevice() {
        val r = PhoneCoreDiagnostics.readiness(PhoneCoreDiagnostics.RuntimeFacts(true,true,true,true,true,true,true,true,true,true,true))
        assertTrue(r.softwarePrerequisitesReady)
        assertTrue(r.physicalDeviceValidated)
        assertTrue(r.fullyValidated)
        assertTrue(r.capabilities.all { it.state == PhoneCoreDiagnostics.State.READY })
    }

    @Test fun activeSimIsRequiredForSmsSendReadiness() {
        val r = PhoneCoreDiagnostics.readiness(PhoneCoreDiagnostics.RuntimeFacts(true,true,true,true,true,true,true,true,false,true,true))
        assertEquals(PhoneCoreDiagnostics.State.LIMITED, r.capabilities.first { it.id == "SMS_SEND" }.state)
        assertFalse(r.softwarePrerequisitesReady)
        assertFalse(r.fullyValidated)
    }
}
