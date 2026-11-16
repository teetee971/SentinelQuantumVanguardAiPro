package com.sentinel.quantum.ui.presentation

import com.sentinel.quantum.security.SecurityAudit
import com.sentinel.quantum.ui.design.SentinelStatus

/** Presentation-only mapping of Android permission truth into D.1 status semantics. */
object SecurityAuditPermissionPresentation {
    fun status(
        grantModel: SecurityAudit.PermissionGrantModel,
        granted: Boolean
    ): SentinelStatus = when (grantModel) {
        SecurityAudit.PermissionGrantModel.RUNTIME_USER,
        SecurityAudit.PermissionGrantModel.INSTALL_TIME ->
            if (granted) SentinelStatus.OK else SentinelStatus.WARN

        SecurityAudit.PermissionGrantModel.SYSTEM_CONTROLLED -> SentinelStatus.LIMITED
    }
}
