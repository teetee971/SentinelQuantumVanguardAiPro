package com.sentinel.quantum.ui.presentation

import com.sentinel.quantum.ui.design.SentinelStatus

/**
 * Conservative presentation of the Protection/Phone Core overview.
 *
 * Software prerequisites can require attention, but software readiness alone is not enough to
 * claim global device protection or physical Phone Core validation. Consequently this projection
 * never produces OK or BAD from these prerequisite booleans alone.
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

    /**
     * A single prerequisite channel being ready is still only software readiness.
     * It must never be rendered as an operationally validated OK state.
     */
    fun prerequisiteStatus(ready: Boolean): SentinelStatus =
        if (ready) SentinelStatus.LIMITED else SentinelStatus.WARN
}
