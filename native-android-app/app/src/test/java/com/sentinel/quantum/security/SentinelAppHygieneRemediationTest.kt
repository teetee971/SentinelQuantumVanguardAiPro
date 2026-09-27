package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Test

class SentinelAppHygieneRemediationTest {
    @Test
    fun staleUsageOnlyOffersSystemControlledUninstall() {
        assertEquals(
            SentinelAppHygieneRemediation.Action.OFFER_SYSTEM_UNINSTALL_FLOW,
            SentinelAppHygieneRemediation.actionFor(
                SentinelAppUsagePolicy.Observation.STALE_USAGE_CONFIRMED
            )
        )
    }

    @Test
    fun missingEvidenceRequestsObservabilityInsteadOfDeletion() {
        assertEquals(
            SentinelAppHygieneRemediation.Action.REQUEST_USAGE_ACCESS,
            SentinelAppHygieneRemediation.actionFor(
                SentinelAppUsagePolicy.Observation.NOT_OBSERVABLE
            )
        )
    }

    @Test
    fun recentUsageOffersNoCleanupAction() {
        assertEquals(
            SentinelAppHygieneRemediation.Action.NONE,
            SentinelAppHygieneRemediation.actionFor(
                SentinelAppUsagePolicy.Observation.RECENTLY_USED
            )
        )
    }
}
