package com.sentinel.quantum.telecom

import com.sentinel.quantum.security.CallRuleEngine
import com.sentinel.quantum.security.CallScreeningEnforcementPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class TelecomEnforcementBoundaryTest {
    @Test
    fun regulatoryEnrichmentCannotMintCallBlockAuthority() {
        val decision = CallRuleEngine().evaluate("+1 202 555 0123")

        assertEquals(CallRuleEngine.Action.ALLOW, decision.action)
        assertEquals(
            CallRuleEngine.Action.ALLOW,
            CallScreeningEnforcementPolicy.actionFor(decision)
        )
    }
}
