package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Test

class FamilySafetyPolicyTest {
    @Test fun emergencyAlwaysAllowed() {
        val d = FamilySafetyPolicy.decide(FamilySafetyPolicy.Context(FamilySafetyPolicy.Profile.ASSISTED, FamilySafetyPolicy.Risk.PREMIUM_RATE_CALLBACK, true))
        assertEquals(FamilySafetyPolicy.Action.ALLOW, d.action)
    }

    @Test fun assistedPremiumCallbackRequiresConfirmation() {
        val d = FamilySafetyPolicy.decide(FamilySafetyPolicy.Context(FamilySafetyPolicy.Profile.ASSISTED, FamilySafetyPolicy.Risk.PREMIUM_RATE_CALLBACK, false))
        assertEquals(FamilySafetyPolicy.Action.REQUIRE_CONFIRMATION, d.action)
    }

    @Test fun assistedUnknownCallerWarnsButDoesNotBlock() {
        val d = FamilySafetyPolicy.decide(FamilySafetyPolicy.Context(FamilySafetyPolicy.Profile.ASSISTED, FamilySafetyPolicy.Risk.UNKNOWN_CALLER, false))
        assertEquals(FamilySafetyPolicy.Action.WARN, d.action)
    }
}
