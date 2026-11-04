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
            ConnectedServiceAccessPolicy.evaluate(ConnectedAccessSnapshot(true, true, true, false))
        )
    }

    @Test fun verifiedValidEntitledUserCanSeeConnectedFeature() {
        assertEquals(
            ConnectedAccessDecision.ALLOW_PRESENTATION,
            ConnectedServiceAccessPolicy.evaluate(ConnectedAccessSnapshot(true, true, true, true))
        )
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
