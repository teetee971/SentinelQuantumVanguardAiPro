package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Test

class CallScreeningEnforcementPolicyTest {
    private fun decision(
        action: CallRuleEngine.Action,
        source: CallRuleEngine.RuleSource
    ) = CallRuleEngine.Decision(action, "TEST", null, source)

    @Test fun explicitUserBlockIsEnforced() {
        assertEquals(
            CallRuleEngine.Action.BLOCK,
            CallScreeningEnforcementPolicy.actionFor(
                decision(CallRuleEngine.Action.BLOCK, CallRuleEngine.RuleSource.USER)
            )
        )
    }

    @Test fun reputationCannotRejectCall() {
        assertEquals(
            CallRuleEngine.Action.ALLOW,
            CallScreeningEnforcementPolicy.actionFor(
                decision(CallRuleEngine.Action.BLOCK, CallRuleEngine.RuleSource.SIGNED_REPUTATION)
            )
        )
    }

    @Test fun unknownSourceCannotRejectCall() {
        assertEquals(
            CallRuleEngine.Action.ALLOW,
            CallScreeningEnforcementPolicy.actionFor(
                decision(CallRuleEngine.Action.BLOCK, CallRuleEngine.RuleSource.NONE)
            )
        )
    }

    @Test fun signedReputationMayOnlySilence() {
        assertEquals(
            CallRuleEngine.Action.SILENCE,
            CallScreeningEnforcementPolicy.actionFor(
                decision(CallRuleEngine.Action.SILENCE, CallRuleEngine.RuleSource.SIGNED_REPUTATION)
            )
        )
    }

    @Test fun unexpectedUserSilenceFailsOpen() {
        assertEquals(
            CallRuleEngine.Action.ALLOW,
            CallScreeningEnforcementPolicy.actionFor(
                decision(CallRuleEngine.Action.SILENCE, CallRuleEngine.RuleSource.USER)
            )
        )
    }

    @Test fun allowRemainsAllow() {
        for (source in CallRuleEngine.RuleSource.values()) {
            assertEquals(
                CallRuleEngine.Action.ALLOW,
                CallScreeningEnforcementPolicy.actionFor(
                    decision(CallRuleEngine.Action.ALLOW, source)
                )
            )
        }
    }
}
