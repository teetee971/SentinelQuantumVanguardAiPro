package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Test

class FamilySafetyPolicyTest {
    @Test fun emergencyAlwaysAllowed() {
        assertEquals(
            FamilySafetyPolicy.Action.ALLOW,
            FamilySafetyPolicy.decide(FamilySafetyPolicy.Context(
                FamilySafetyPolicy.Profile.ASSISTED,
                FamilySafetyPolicy.Risk.WANGIRI_CALLBACK,
                platformEmergency = true
            ))
        )
    }

    @Test fun assistedUnknownWarnsWithoutBlocking() {
        assertEquals(
            FamilySafetyPolicy.Action.WARN,
            FamilySafetyPolicy.decide(FamilySafetyPolicy.Context(
                FamilySafetyPolicy.Profile.ASSISTED,
                FamilySafetyPolicy.Risk.UNKNOWN_CALLER,
                platformEmergency = false
            ))
        )
    }

    @Test fun assistedRiskyCallbackRequiresConfirmation() {
        for (risk in listOf(
            FamilySafetyPolicy.Risk.PREMIUM_RATE_CALLBACK,
            FamilySafetyPolicy.Risk.WANGIRI_CALLBACK
        )) {
            assertEquals(
                FamilySafetyPolicy.Action.REQUIRE_CONFIRMATION,
                FamilySafetyPolicy.decide(FamilySafetyPolicy.Context(
                    FamilySafetyPolicy.Profile.ASSISTED, risk, false
                ))
            )
        }
    }

    @Test fun standardProfileDoesNotAddRestrictions() {
        assertEquals(
            FamilySafetyPolicy.Action.ALLOW,
            FamilySafetyPolicy.decide(FamilySafetyPolicy.Context(
                FamilySafetyPolicy.Profile.STANDARD,
                FamilySafetyPolicy.Risk.PREMIUM_RATE_CALLBACK,
                false
            ))
        )
    }
}
