package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PhoneProtectionListTruthTest {
    private val now = 2_000_000_000_000L

    @Test fun absentOrUnverifiedSourceIsNeverActive() {
        assertEquals(
            PhoneProtectionListTruth.ListStatus.UNAVAILABLE,
            PhoneProtectionListTruth.status(
                PhoneProtectionListTruth.SourceFacts(
                    packagePresent = false,
                    verified = false,
                    enabledByUser = true,
                    itemCount = 10
                ),
                now
            )
        )
        assertEquals(
            PhoneProtectionListTruth.ListStatus.UNAVAILABLE,
            PhoneProtectionListTruth.status(
                PhoneProtectionListTruth.SourceFacts(
                    packagePresent = true,
                    verified = false,
                    enabledByUser = true,
                    itemCount = 10
                ),
                now
            )
        )
    }

    @Test fun expiredSourceCannotBePresentedAsActive() {
        assertEquals(
            PhoneProtectionListTruth.ListStatus.EXPIRED,
            PhoneProtectionListTruth.status(
                PhoneProtectionListTruth.SourceFacts(
                    packagePresent = true,
                    verified = true,
                    enabledByUser = true,
                    itemCount = 10,
                    expiresAtMs = now
                ),
                now
            )
        )
    }

    @Test fun verifiedSourceReflectsUserActivation() {
        val enabled = PhoneProtectionListTruth.SourceFacts(true, true, true, 22)
        val disabled = enabled.copy(enabledByUser = false)
        assertEquals(PhoneProtectionListTruth.ListStatus.ACTIVE, PhoneProtectionListTruth.status(enabled, now))
        assertEquals(PhoneProtectionListTruth.ListStatus.DISABLED, PhoneProtectionListTruth.status(disabled, now))
    }

    @Test fun oneCommunitySignalNeverBecomesSpamConsensus() {
        assertEquals(
            PhoneProtectionListTruth.CommunityVerdict.INSUFFICIENT,
            PhoneProtectionListTruth.communityVerdict(1, "spam_probable")
        )
        assertFalse(PhoneProtectionListTruth.mayAutomaticallyBlockFromCommunity(1, "spam_probable"))
    }

    @Test fun moderatedMultiSignalLabelCanBeShownAsProbableButNeverAutoBlocks() {
        assertEquals(
            PhoneProtectionListTruth.CommunityVerdict.SPAM_PROBABLE,
            PhoneProtectionListTruth.communityVerdict(2, "spam_probable")
        )
        assertFalse(PhoneProtectionListTruth.mayAutomaticallyBlockFromCommunity(50, "spam_probable"))
    }

    @Test fun zeroSignalsRemainUnevaluated() {
        assertEquals(
            PhoneProtectionListTruth.CommunityVerdict.NOT_EVALUATED,
            PhoneProtectionListTruth.communityVerdict(0, "spam_probable")
        )
    }
}
