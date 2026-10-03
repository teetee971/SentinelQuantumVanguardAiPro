package com.sentinel.quantum.security

import android.Manifest

/**
 * Least-privilege policy for the SMS composer.
 *
 * Reading or receiving SMS is deliberately outside the send path. Keeping this pure and
 * independently tested prevents activation UX changes from silently widening composer permissions.
 */
object SmsSendPermissionPolicy {
    fun permissionsFor(blockers: Set<SmsActivationDiagnostics.Blocker>): Array<String> =
        buildList {
            if (SmsActivationDiagnostics.Blocker.SEND_SMS_PERMISSION_REQUIRED in blockers) {
                add(Manifest.permission.SEND_SMS)
            }
            if (SmsActivationDiagnostics.Blocker.READ_PHONE_STATE_PERMISSION_REQUIRED in blockers) {
                add(Manifest.permission.READ_PHONE_STATE)
            }
        }.toTypedArray()
}

/**
 * Staged least-privilege policy for the Phone Core SMS activation wizard.
 *
 * The SMS role must already be held. Runtime permissions are then requested in two distinct
 * phases: first the send/SIM path, and only after that path is complete the inbox path. This
 * prevents the activation UI from aggregating SEND_SMS, READ_PHONE_STATE, READ_SMS and RECEIVE_SMS
 * into one broad request and keeps the Android permission prompts aligned with the capability the
 * user is activating.
 */
object SmsActivationPermissionPolicy {
    fun permissionsFor(
        roleState: SmsActivationDiagnostics.SmsRoleState,
        blockers: Set<SmsActivationDiagnostics.Blocker>,
    ): Array<String> {
        if (roleState != SmsActivationDiagnostics.SmsRoleState.HELD) return emptyArray()

        val sendPermissions = SmsSendPermissionPolicy.permissionsFor(blockers)
        if (sendPermissions.isNotEmpty()) return sendPermissions

        return SmsInboxPermissionPolicy.permissionsFor(roleState, blockers)
    }
}

/**
 * Least-privilege policy for SMS inbox/conversation activation.
 *
 * The SMS default-role state is part of the pure policy so the fail-closed role boundary is
 * directly testable. Sending and SIM access stay outside this path.
 */
object SmsInboxPermissionPolicy {
    fun permissionsFor(
        roleState: SmsActivationDiagnostics.SmsRoleState,
        blockers: Set<SmsActivationDiagnostics.Blocker>,
    ): Array<String> {
        if (roleState != SmsActivationDiagnostics.SmsRoleState.HELD) return emptyArray()
        return buildList {
            if (SmsActivationDiagnostics.Blocker.READ_SMS_PERMISSION_REQUIRED in blockers) {
                add(Manifest.permission.READ_SMS)
            }
            if (SmsActivationDiagnostics.Blocker.RECEIVE_SMS_PERMISSION_REQUIRED in blockers) {
                add(Manifest.permission.RECEIVE_SMS)
            }
        }.toTypedArray()
    }
}
