package com.sentinel.quantum.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectedServiceAccessPolicyTest {
    @Test fun guestCannotAccessConnectedServices() {
        assertEquals(
            ConnectedAccessDecision.SIGN_IN_REQUIRED,
            ConnectedServiceAccessPolicy.evaluate(ConnectedAccessSnapshot(false, false, false, false))
        )
    }

    @Test fun emailMustBeVerified() {
        assertEquals(
            ConnectedAccessDecision.EMAIL_VERIFICATION_REQUIRED,
            ConnectedServiceAccessPolicy.evaluate(ConnectedAccessSnapshot(true, false, true, true))
        )
    }

    @Test fun staleSessionCannotAccessConnectedServices() {
        assertEquals(
            ConnectedAccessDecision.SESSION_REVALIDATION_REQUIRED,
            ConnectedServiceAccessPolicy.evaluate(ConnectedAccessSnapshot(true, true, false, true))
        )
    }

    @Test fun entitlementIsRequired() {
        assertEquals(
            ConnectedAccessDecision.ENTITLEMENT_REQUIRED,
            ConnectedServiceAccessPolicy.evaluate(ConnectedAccessSnapshot(true, true, true, false, premiumFeature = true))
        )
    }

    @Test fun verifiedValidEntitledUserCanSeeConnectedFeature() {
        assertEquals(
            ConnectedAccessDecision.ALLOW_PRESENTATION,
            ConnectedServiceAccessPolicy.evaluate(ConnectedAccessSnapshot(true, true, true, true, premiumFeature = true))
        )
    }

    @Test fun freeConnectedFeaturesDoNotRequirePremiumEntitlement() {
        assertEquals(
            ConnectedAccessDecision.ALLOW_PRESENTATION,
            ConnectedServiceAccessPolicy.evaluate(
                ConnectedAccessSnapshot(true, true, true, false, premiumFeature = false)
            )
        )
    }

    @Test fun premiumFeatureRequiresEntitlement() {
        assertEquals(
            ConnectedAccessDecision.ENTITLEMENT_REQUIRED,
            ConnectedServiceAccessPolicy.evaluate(
                ConnectedAccessSnapshot(true, true, true, false, premiumFeature = true)
            )
        )
    }

    @Test fun invalidSessionCannotBeMaskedByUnverifiedEmail() {
        assertEquals(
            ConnectedAccessDecision.SESSION_REVALIDATION_REQUIRED,
            ConnectedServiceAccessPolicy.evaluate(
                ConnectedAccessSnapshot(true, false, false, false)
            )
        )
    }

    @Test fun onlyVerifiedValidSessionsCanPresentConnectedFeatures() {
        val values = listOf(false, true)
        for (authenticated in values) for (verified in values)
            for (sessionValid in values) for (entitlement in values)
                for (premium in values) {
                    val snapshot = ConnectedAccessSnapshot(
                        authenticated, verified, sessionValid, entitlement, premium
                    )
                    val allowed = ConnectedServiceAccessPolicy.evaluate(snapshot) ==
                        ConnectedAccessDecision.ALLOW_PRESENTATION
                    val expected = authenticated && verified && sessionValid &&
                        (!premium || entitlement)
                    assertEquals("Unexpected access for $snapshot", expected, allowed)
                }
    }

    @Test fun invalidIdentityAlwaysFailsClosed() {
        val combinations = listOf(false, true)
        for (verified in combinations) for (session in combinations) for (entitlement in combinations) {
            assertEquals(
                ConnectedAccessDecision.SIGN_IN_REQUIRED,
                ConnectedServiceAccessPolicy.evaluate(ConnectedAccessSnapshot(false, verified, session, entitlement))
            )
        }
    }
}
