package com.sentinel.quantum.security

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnknownCallerScreeningTest {
    @Test fun unknownCallerReceivesUnknownCallerPolicyDecision() {
        val number = "+33612345678"
        val engine = CallRuleEngine(
            silenceOutsideDirectory = false,
            directoryReady = true,
            directoryContains = { false }
        ).evaluate(number)
        assertEquals(CallRuleEngine.Action.ALLOW, engine.action)
        assertEquals("NO_MATCHING_RULE", engine.reason)
        val context = unknown(modeEnabled = true)
        val policy = UnknownCallerPolicy.decide(context)
        val screened = composeScreeningDecision(engine, context)
        assertEquals(policy.action.name, screened.action.name)
        assertEquals(policy.reason, screened.reason)
        assertEquals(CallRuleEngine.Action.SILENCE, screened.action)
        assertEquals("UNKNOWN_CALLER", screened.reason)
    }

    @Test fun directorySilenceUsesTheUnknownCallerPolicyReason() {
        val engine = CallRuleEngine.Decision(
            CallRuleEngine.Action.SILENCE,
            "NOT_IN_DIRECTORY",
            "+33612345678",
            CallRuleEngine.RuleSource.USER
        )
        val screened = composeScreeningDecision(engine, unknown(modeEnabled = true))
        assertEquals(CallRuleEngine.Action.SILENCE, screened.action)
        assertEquals("UNKNOWN_CALLER", screened.reason)
    }

    @Test fun favoriteAllowlistAndRepeatCallStayAllowed() {
        val engine = CallRuleEngine.Decision(
            CallRuleEngine.Action.SILENCE,
            "NOT_IN_DIRECTORY",
            "+33612345678",
            CallRuleEngine.RuleSource.USER
        )
        assertEquals(
            "FAVORITE",
            composeScreeningDecision(engine, unknown(modeEnabled = true, favorite = true)).reason
        )
        assertEquals(
            CallRuleEngine.Action.ALLOW,
            composeScreeningDecision(engine, unknown(modeEnabled = true, favorite = true)).action
        )
        assertEquals(
            "ALLOWLIST",
            composeScreeningDecision(engine, unknown(modeEnabled = true, explicitlyAllowed = true)).reason
        )
        assertEquals(
            "REPEAT_CALL",
            composeScreeningDecision(engine, unknown(modeEnabled = true, repeatCall = true)).reason
        )
    }

    @Test fun modeOffLeavesTheEngineDecision() {
        val engine = CallRuleEngine().evaluate("+33612345678")
        val screened = composeScreeningDecision(engine, unknown(modeEnabled = false))
        assertEquals(engine.action, screened.action)
        assertEquals(engine.reason, screened.reason)
    }

    @Test fun blockPrefixSilenceAmbiguousLineAndEmergencyStayUntouched() {
        assertTrue(screeningMustFailOpen(null))
        assertTrue(screeningMustFailOpen(true))
        assertFalse(screeningMustFailOpen(false))
        val blocked = decision(CallRuleEngine.Action.BLOCK, "USER_EXACT_BLOCK")
        assertEquals("USER_EXACT_BLOCK", composeScreeningDecision(blocked, unknown(true)).reason)
        val prefix = decision(CallRuleEngine.Action.SILENCE, "USER_PREFIX_SILENCE")
        assertEquals("USER_PREFIX_SILENCE", composeScreeningDecision(prefix, unknown(true)).reason)
        val premium = decision(CallRuleEngine.Action.SILENCE, "USER_PREMIUM_SILENCE")
        assertEquals("USER_PREMIUM_SILENCE", composeScreeningDecision(premium, unknown(true)).reason)
        val ambiguous = decision(CallRuleEngine.Action.ALLOW, "AMBIGUOUS_LINE")
        assertEquals("AMBIGUOUS_LINE", composeScreeningDecision(ambiguous, unknown(true)).reason)
        val missing = decision(CallRuleEngine.Action.ALLOW, "INVALID_OR_UNAVAILABLE_NUMBER")
        assertEquals(
            "INVALID_OR_UNAVAILABLE_NUMBER",
            composeScreeningDecision(missing, unknown(true)).reason
        )
        val emergency = decision(CallRuleEngine.Action.ALLOW, "NO_MATCHING_RULE")
        val kept = composeScreeningDecision(emergency, unknown(modeEnabled = true, platformEmergency = true))
        assertEquals(CallRuleEngine.Action.ALLOW, kept.action)
        assertEquals("NO_MATCHING_RULE", kept.reason)
    }

    @Test fun unknownCallerReasonIsFrench() {
        assertEquals(
            "Numéro inconnu mis en silencieux par choix",
            PhoneCoreFrenchLabels.reason("UNKNOWN_CALLER")
        )
        assertFalse(PhoneCoreFrenchLabels.reason("UNKNOWN_CALLER").contains("non traduit"))
        assertFalse(PhoneCoreFrenchLabels.reason("FAVORITE").contains("non traduit"))
        assertFalse(PhoneCoreFrenchLabels.reason("ALLOWLIST").contains("non traduit"))
        assertFalse(PhoneCoreFrenchLabels.reason("REPEAT_CALL").contains("non traduit"))
        assertFalse(PhoneCoreFrenchLabels.reason("CONTACT").contains("non traduit"))
    }

    @Test fun screeningServiceAppliesThePolicyAfterFailOpen() {
        val source = File(
            "src/main/java/com/sentinel/quantum/security/SentinelCallScreeningService.kt"
        ).readText()
        val failOpen = source.indexOf("if (screeningMustFailOpen(emergency))")
        val compose = source.indexOf("composeScreeningDecision(")
        val project = source.indexOf("projectScreeningDecision(")
        assertTrue(failOpen >= 0)
        assertTrue(compose > failOpen)
        assertTrue(project > compose)
        val failOpenBlock = source.substring(failOpen, compose)
        assertTrue(failOpenBlock.contains("CallResponse.Builder().build()"))
        assertFalse(failOpenBlock.contains("UnknownCallerPolicy"))
        assertFalse(failOpenBlock.contains("composeScreeningDecision"))
        assertTrue(source.contains("modeEnabled = snapshot.silenceOutsideDirectory && directory.ready"))
        assertTrue(source.contains("platformEmergency = emergency == true"))
        assertTrue(source.contains("favorite = false"))
        assertTrue(source.contains("explicitlyAllowed = false"))
        assertTrue(source.contains("repeatCall = false"))
        assertFalse(source.contains("PhoneFavoriteStore"))
        assertFalse(source.contains("getSharedPreferences"))
    }

    private fun unknown(
        modeEnabled: Boolean,
        platformEmergency: Boolean = false,
        inContacts: Boolean = false,
        favorite: Boolean = false,
        explicitlyAllowed: Boolean = false,
        repeatCall: Boolean = false
    ) = UnknownCallerPolicy.Context(
        modeEnabled,
        platformEmergency,
        inContacts,
        favorite,
        explicitlyAllowed,
        repeatCall
    )

    private fun decision(action: CallRuleEngine.Action, reason: String) = CallRuleEngine.Decision(
        action,
        reason,
        "+33612345678",
        CallRuleEngine.RuleSource.USER
    )
}
