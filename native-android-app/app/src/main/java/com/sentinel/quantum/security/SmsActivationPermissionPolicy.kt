package com.sentinel.quantum.security

import android.Manifest

/**
 * Pure least-privilege policy for SMS runtime permission requests.
 *
 * The activation surface may request the complete set needed by the default-SMS client, while
 * the composer requests only permissions needed to submit an outbound SMS. Both paths remain
 * blocked until Android confirms the SMS role is held.
 */
object SmsActivationPermissionPolicy {
    fun activationPermissions(snapshot: SmsActivationDiagnostics.Snapshot): Array<String> {
        if (snapshot.smsRoleState != SmsActivationDiagnostics.SmsRoleState.HELD) return emptyArray()
        return buildList {
            if (SmsActivationDiagnostics.Blocker.SEND_SMS_PERMISSION_REQUIRED in snapshot.blockers) add(Manifest.permission.SEND_SMS)
            if (SmsActivationDiagnostics.Blocker.READ_SMS_PERMISSION_REQUIRED in snapshot.blockers) add(Manifest.permission.READ_SMS)
            if (SmsActivationDiagnostics.Blocker.RECEIVE_SMS_PERMISSION_REQUIRED in snapshot.blockers) add(Manifest.permission.RECEIVE_SMS)
            if (SmsActivationDiagnostics.Blocker.READ_PHONE_STATE_PERMISSION_REQUIRED in snapshot.blockers) add(Manifest.permission.READ_PHONE_STATE)
        }.toTypedArray()
    }

    fun sendPermissions(snapshot: SmsActivationDiagnostics.Snapshot): Array<String> {
        if (!snapshot.needsSendRuntimePermissions) return emptyArray()
        return buildList {
            if (SmsActivationDiagnostics.Blocker.SEND_SMS_PERMISSION_REQUIRED in snapshot.blockers) add(Manifest.permission.SEND_SMS)
            if (SmsActivationDiagnostics.Blocker.READ_PHONE_STATE_PERMISSION_REQUIRED in snapshot.blockers) add(Manifest.permission.READ_PHONE_STATE)
        }.toTypedArray()
    }
}
