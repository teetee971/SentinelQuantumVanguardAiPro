package com.sentinel.quantum.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MmsDownloadRecoveryProviderPolicyTest {
    @Test
    fun providerRejectionRetriesBeforeCleanupDeadline() {
        val rejected = IncomingMmsConversationStore.ProjectResult.Rejected(
            reason = "MMS_PROVIDER_RECOVERY_PENDING",
            cleanupConfirmed = false
        )
        assertTrue(
            MmsDownloadRecovery.shouldRetryProviderProjection(
                providerResult = rejected,
                allowQuarantine = false
            )
        )
    }

    @Test
    fun providerRejectionMayFinalizePrivateOnlyAtCleanupDeadline() {
        val rejected = IncomingMmsConversationStore.ProjectResult.Rejected(
            reason = "MMS_GROUP_SELF_IDENTITY_UNAVAILABLE",
            cleanupConfirmed = true
        )
        assertFalse(
            MmsDownloadRecovery.shouldRetryProviderProjection(
                providerResult = rejected,
                allowQuarantine = true
            )
        )
    }

    @Test
    fun successfulProviderProjectionNeverRequestsRecoveryRetry() {
        val ready = IncomingMmsConversationStore.ProjectResult.Ready(
            providerMessageId = 42L,
            replay = false
        )
        assertFalse(
            MmsDownloadRecovery.shouldRetryProviderProjection(
                providerResult = ready,
                allowQuarantine = false
            )
        )
        assertFalse(
            MmsDownloadRecovery.shouldRetryProviderProjection(
                providerResult = null,
                allowQuarantine = false
            )
        )
    }
}
