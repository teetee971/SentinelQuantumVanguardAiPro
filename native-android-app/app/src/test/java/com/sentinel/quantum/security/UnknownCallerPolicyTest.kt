package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Test

class UnknownCallerPolicyTest {
    @Test fun emergencyAlwaysAllowed() {
        val d = UnknownCallerPolicy.decide(UnknownCallerPolicy.Context(true,true,false,false,false,false))
        assertEquals(UnknownCallerPolicy.Action.ALLOW, d.action)
    }

    @Test fun explicitAllowlistWins() {
        val d = UnknownCallerPolicy.decide(UnknownCallerPolicy.Context(true,false,false,false,true,false))
        assertEquals("ALLOWLIST", d.reason)
    }

    @Test fun repeatCallCanPass() {
        val d = UnknownCallerPolicy.decide(UnknownCallerPolicy.Context(true,false,false,false,false,true))
        assertEquals(UnknownCallerPolicy.Action.ALLOW, d.action)
    }

    @Test fun unknownCallerIsSilencedOnlyWhenModeEnabled() {
        val enabled = UnknownCallerPolicy.decide(UnknownCallerPolicy.Context(true,false,false,false,false,false))
        val disabled = UnknownCallerPolicy.decide(UnknownCallerPolicy.Context(false,false,false,false,false,false))
        assertEquals(UnknownCallerPolicy.Action.SILENCE, enabled.action)
        assertEquals(UnknownCallerPolicy.Action.ALLOW, disabled.action)
    }
}
