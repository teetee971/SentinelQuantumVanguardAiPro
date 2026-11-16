package com.sentinel.quantum.ui.presentation

import com.sentinel.quantum.security.SecurityAudit
import com.sentinel.quantum.ui.design.SentinelStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class SecurityAuditPermissionPresentationTest {

    @Test
    fun runtimePermissionUsesObservedGrantStateWithoutDeclaringDanger() {
        assertEquals(
            SentinelStatus.OK,
            SecurityAuditPermissionPresentation.status(
                SecurityAudit.PermissionGrantModel.RUNTIME_USER,
                granted = true
            )
        )
        assertEquals(
            SentinelStatus.WARN,
            SecurityAuditPermissionPresentation.status(
                SecurityAudit.PermissionGrantModel.RUNTIME_USER,
                granted = false
            )
        )
    }

    @Test
    fun installTimePermissionUsesAvailabilityWithoutSynthesizingThreat() {
        assertEquals(
            SentinelStatus.OK,
            SecurityAuditPermissionPresentation.status(
                SecurityAudit.PermissionGrantModel.INSTALL_TIME,
                granted = true
            )
        )
        assertEquals(
            SentinelStatus.WARN,
            SecurityAuditPermissionPresentation.status(
                SecurityAudit.PermissionGrantModel.INSTALL_TIME,
                granted = false
            )
        )
    }

    @Test
    fun systemControlledPermissionRemainsLimitedEvenWhenReportedGranted() {
        assertEquals(
            SentinelStatus.LIMITED,
            SecurityAuditPermissionPresentation.status(
                SecurityAudit.PermissionGrantModel.SYSTEM_CONTROLLED,
                granted = true
            )
        )
        assertEquals(
            SentinelStatus.LIMITED,
            SecurityAuditPermissionPresentation.status(
                SecurityAudit.PermissionGrantModel.SYSTEM_CONTROLLED,
                granted = false
            )
        )
    }
}
