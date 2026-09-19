package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneCoreDiagnosticsTest {
    @Test fun doesNotClaimReadyWithoutRuntimePrerequisites() {
        val states = PhoneCoreDiagnostics.evaluate(PhoneCoreDiagnostics.RuntimeFacts(false,false,false,false,false,false,false,false))
        assertEquals(PhoneCoreDiagnostics.State.LOCKED, states.first { it.id == "DIALER" }.state)
        assertEquals(PhoneCoreDiagnostics.State.LOCKED, states.first { it.id == "MMS_ATTACHMENTS" }.state)
        assertEquals(PhoneCoreDiagnostics.State.LIMITED, states.first { it.id == "PHYSICAL_DEVICE" }.state)
    }

    @Test fun readyRequiresObservedRoleAndPermission() {
        val states = PhoneCoreDiagnostics.evaluate(PhoneCoreDiagnostics.RuntimeFacts(true,true,true,true,true,true,false,false))
        assertEquals(PhoneCoreDiagnostics.State.READY, states.first { it.id == "DIALER" }.state)
        assertEquals(PhoneCoreDiagnostics.State.READY, states.first { it.id == "SMS_SEND" }.state)
        assertEquals(PhoneCoreDiagnostics.State.LOCKED, states.first { it.id == "MMS_ATTACHMENTS" }.state)
    }
}
