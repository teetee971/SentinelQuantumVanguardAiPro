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
