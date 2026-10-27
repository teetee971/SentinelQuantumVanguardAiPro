package com.sentinel.quantum

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneCoreRoleRequestPolicyTest {
    @Test fun unavailableRoleIsBlockedWithExplicitStatus() {
        assertEquals(
            PhoneCoreRoleRequestPolicy.Status.ROLE_UNAVAILABLE,
            PhoneCoreRoleRequestPolicy.afterClick(roleAvailable = false, intentCreated = false)
        )
        assertEquals(
            "Le rôle Téléphone est indisponible sur cet appareil.",
            PhoneCoreRoleRequestPolicy.message(PhoneCoreRoleRequestPolicy.Status.ROLE_UNAVAILABLE)
        )
    }

    @Test fun availableRoleRequiresAnIntentBeforeLaunch() {
        assertEquals(
            PhoneCoreRoleRequestPolicy.Status.INTENT_UNAVAILABLE,
            PhoneCoreRoleRequestPolicy.afterClick(roleAvailable = true, intentCreated = false)
        )
    }

    @Test fun launchAndAndroidResultHaveDistinctOutcomes() {
        assertEquals(
            PhoneCoreRoleRequestPolicy.Status.AWAITING_ANDROID,
            PhoneCoreRoleRequestPolicy.afterLaunch(success = true)
        )
        assertEquals(
            PhoneCoreRoleRequestPolicy.Status.LAUNCH_FAILED,
            PhoneCoreRoleRequestPolicy.afterLaunch(success = false)
        )
        assertEquals(
            PhoneCoreRoleRequestPolicy.Status.COMPLETED_HELD,
            PhoneCoreRoleRequestPolicy.afterResult(resultCode = 0, roleHeld = true)
        )
        assertEquals(
            PhoneCoreRoleRequestPolicy.Status.CANCELLED,
            PhoneCoreRoleRequestPolicy.afterResult(resultCode = 0, roleHeld = false)
        )
        assertEquals(
            PhoneCoreRoleRequestPolicy.Status.COMPLETED_NOT_HELD,
            PhoneCoreRoleRequestPolicy.afterResult(resultCode = -1, roleHeld = false)
        )
    }

    @Test fun androidDecisionDistinguishesAcceptanceFromEffectiveRoleOwnership() {
        assertEquals(
            PhoneCoreRoleRequestPolicy.AndroidDecision.ACCEPTED_ROLE_HELD,
            PhoneCoreRoleRequestPolicy.androidDecision(resultCode = -1, roleHeld = true)
        )
        assertEquals(
            PhoneCoreRoleRequestPolicy.AndroidDecision.ACCEPTED_ROLE_NOT_HELD,
            PhoneCoreRoleRequestPolicy.androidDecision(resultCode = -1, roleHeld = false)
        )
        assertEquals(
            PhoneCoreRoleRequestPolicy.AndroidDecision.REFUSED_OR_CANCELLED,
            PhoneCoreRoleRequestPolicy.androidDecision(resultCode = 0, roleHeld = false)
        )
        assertEquals(
            PhoneCoreRoleRequestPolicy.AndroidDecision.ANDROID_RESULT_NOT_OK,
            PhoneCoreRoleRequestPolicy.androidDecision(resultCode = 1, roleHeld = false)
        )
    }
}
