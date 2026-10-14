package com.sentinel.quantum.ui.presentation

import com.sentinel.quantum.ui.design.SentinelStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ProtectionOverviewPresentationTest {

    @Test
    fun missingPhoneCorePrerequisitesRequireAttention() {
        assertEquals(
            SentinelStatus.WARN,
            ProtectionOverviewPresentation.status(
                phoneCoreReady = false,
                callPrerequisitesReady = false,
                messagePrerequisitesReady = true
            )
        )
        assertEquals(
            SentinelStatus.WARN,
            ProtectionOverviewPresentation.status(
                phoneCoreReady = false,
                callPrerequisitesReady = true,
                messagePrerequisitesReady = false
            )
        )
    }

    @Test
    fun softwareReadinessRemainsLimitedWithoutGlobalPhysicalProof() {
        assertEquals(
            SentinelStatus.LIMITED,
            ProtectionOverviewPresentation.status(
                phoneCoreReady = true,
                callPrerequisitesReady = true,
                messagePrerequisitesReady = true
            )
        )
    }

    @Test
    fun inconsistentReadyFlagFailsConservatively() {
        assertEquals(
            SentinelStatus.WARN,
            ProtectionOverviewPresentation.status(
                phoneCoreReady = true,
                callPrerequisitesReady = false,
                messagePrerequisitesReady = true
            )
        )
    }
}
