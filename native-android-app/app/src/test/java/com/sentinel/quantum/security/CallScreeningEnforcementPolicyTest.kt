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

    @Test fun signedReputationSilenceFailsOpenPendingQualification() {
        assertEquals(
            CallRuleEngine.Action.ALLOW,
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

    @Test fun exhaustiveActionAndSourceMatrixNeverAccidentallyRejects() {
        for (action in CallRuleEngine.Action.values()) {
            for (source in CallRuleEngine.RuleSource.values()) {
                val actual = CallScreeningEnforcementPolicy.actionFor(decision(action, source))
                val expected = if (
                    action == CallRuleEngine.Action.BLOCK &&
                    source == CallRuleEngine.RuleSource.USER
                ) CallRuleEngine.Action.BLOCK else CallRuleEngine.Action.ALLOW
                assertEquals("action=$action source=$source", expected, actual)
            }
        }
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
