package com.sentinel.quantum.ui.presentation

import com.sentinel.quantum.ui.design.SentinelStatus

/**
 * Conservative presentation of the Protection/Phone Core overview.
 *
 * Software prerequisites can require attention, but software readiness alone is not enough to
 * claim global device protection or physical Phone Core validation. Consequently this projection
 * never produces OK or BAD from these three prerequisite booleans alone.
 */
object ProtectionOverviewPresentation {
    fun status(
        phoneCoreReady: Boolean,
        callPrerequisitesReady: Boolean,
        messagePrerequisitesReady: Boolean
    ): SentinelStatus {
        return if (
            phoneCoreReady &&
            callPrerequisitesReady &&
            messagePrerequisitesReady
        ) {
            SentinelStatus.LIMITED
        } else {
            SentinelStatus.WARN
        }
    }
}
