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

    @Test fun realRuleEngineReputationMatchDoesNotSilenceIncomingCall() {
        val decision = CallRuleEngine(
            reputationSilencePrefixes = setOf("+336")
        ).evaluate("+33612345678")
        assertEquals(CallRuleEngine.Action.SILENCE, decision.action)
        assertEquals(CallRuleEngine.RuleSource.SIGNED_REPUTATION, decision.source)
        assertEquals(CallRuleEngine.Action.ALLOW, CallScreeningEnforcementPolicy.actionFor(decision))
    }

    @Test fun realRuleEngineUnknownCallerFailsOpen() {
        val decision = CallRuleEngine().evaluate(null)
        assertEquals(CallRuleEngine.Action.ALLOW, CallScreeningEnforcementPolicy.actionFor(decision))
    }

    @Test fun realRuleEngineExplicitPrefixBlockStillWorks() {
        val decision = CallRuleEngine(blockedPrefixes = setOf("+336")).evaluate("+33612345678")
        assertEquals(CallRuleEngine.RuleSource.USER, decision.source)
        assertEquals(CallRuleEngine.Action.BLOCK, CallScreeningEnforcementPolicy.actionFor(decision))
    }

    @Test fun internationalCountryPrefixBlocksOnlyOnExplicitUserRule() {
        val candidates = listOf(
            "+1" to "+12025550123",
            "+44" to "+442079460123",
            "+590" to "+590690123456",
            "+850" to "+85012345678",
            "+7" to "+74951234567"
        )
        candidates.forEach { (prefix, number) ->
            val noManualRule = CallRuleEngine().evaluate(number)
            assertEquals(
                "Unlisted $prefix must be allowed",
                CallRuleEngine.Action.ALLOW,
                CallScreeningEnforcementPolicy.actionFor(noManualRule)
            )
            val explicitRule = CallRuleEngine(blockedPrefixes = setOf(prefix)).evaluate(number)
            assertEquals(
                "User-configured $prefix must remain enforceable",
                CallRuleEngine.Action.BLOCK,
                CallScreeningEnforcementPolicy.actionFor(explicitRule)
            )
        }
    }

    @Test fun unknownOrWithheldInternationalNumberIsAllowed() {
        listOf(null, "", "anonymous", "+").forEach { raw ->
            assertEquals(
                CallRuleEngine.Action.ALLOW,
                CallScreeningEnforcementPolicy.actionFor(CallRuleEngine().evaluate(raw))
            )
        }
    }

    @Test fun sharedCallingCodesCannotDistinguishCountries() {
        // +1 covers multiple NANP countries; +7 covers Russia and Kazakhstan.
        // A broad code must not be represented as a verified caller country.
        val northAmerican = CallRuleEngine(blockedPrefixes = setOf("+1"))
        assertEquals(CallRuleEngine.Action.BLOCK, northAmerican.evaluate("+12025550123").action)
        assertEquals(CallRuleEngine.Action.BLOCK, northAmerican.evaluate("+14165550123").action)
        val sharedSeven = CallRuleEngine(blockedPrefixes = setOf("+7"))
        assertEquals(CallRuleEngine.Action.BLOCK, sharedSeven.evaluate("+74951234567").action)
        assertEquals(CallRuleEngine.Action.BLOCK, sharedSeven.evaluate("+77011234567").action)
    }

    @Test fun internationalPrefixDoesNotMatchUnrelatedCallingCode() {
        val engine = CallRuleEngine(blockedPrefixes = setOf("+850"))
        listOf("+33612345678", "+590690123456", "+442079460123").forEach { number ->
            val decision = engine.evaluate(number)
            assertEquals(CallRuleEngine.Action.ALLOW, CallScreeningEnforcementPolicy.actionFor(decision))
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
