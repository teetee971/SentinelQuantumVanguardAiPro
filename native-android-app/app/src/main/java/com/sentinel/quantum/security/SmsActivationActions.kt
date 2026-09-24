package com.sentinel.quantum.security

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony

/**
 * Builds only user-driven actions needed to move the SMS client toward READY.
 *
 * No action is launched here. The UI must present the reason and let the user explicitly trigger
 * the corresponding Android system flow. This keeps role and permission changes outside the
 * diagnostic layer and prevents silent permission escalation.
 */
class SmsActivationActions(private val context: Context) {
    fun roleRequestIntent(): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val manager = context.getSystemService(RoleManager::class.java) ?: return null
        if (!manager.isRoleAvailable(RoleManager.ROLE_SMS)) return null
        val roleHeld = manager.isRoleHeld(RoleManager.ROLE_SMS)
        if (!SmsRoleActivationGate.canRequestRole(isDefaultSmsHandler = roleHeld)) return null
        return manager.createRequestRoleIntent(RoleManager.ROLE_SMS)
    }

    fun permissionsFor(snapshot: SmsActivationDiagnostics.Snapshot): Array<String> {
        if (SmsActivationDiagnostics.Blocker.SMS_ROLE_REQUIRED in snapshot.blockers) {
            return emptyArray()
        }
        return buildList {
            if (SmsActivationDiagnostics.Blocker.SEND_SMS_PERMISSION_REQUIRED in snapshot.blockers) {
                add(Manifest.permission.SEND_SMS)
            }
            if (SmsActivationDiagnostics.Blocker.READ_SMS_PERMISSION_REQUIRED in snapshot.blockers) {
                add(Manifest.permission.READ_SMS)
            }
            if (SmsActivationDiagnostics.Blocker.RECEIVE_SMS_PERMISSION_REQUIRED in snapshot.blockers) {
                add(Manifest.permission.RECEIVE_SMS)
            }
            if (SmsActivationDiagnostics.Blocker.READ_PHONE_STATE_PERMISSION_REQUIRED in snapshot.blockers) {
                add(Manifest.permission.READ_PHONE_STATE)
            }
        }.toTypedArray()
    }

    /**
     * Android 9 and earlier have no RoleManager request contract. Use the platform's dedicated
     * default-SMS chooser rather than dropping the user into the generic default-app settings.
     * The same software-capability gate used on Android 10+ is enforced before exposing the
     * chooser, so legacy devices cannot bypass the fail-closed SMS migration policy.
     * The system still owns the decision and Sentinel never changes the default silently.
     */
    fun legacyDefaultAppsIntent(): Intent? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return null
        val isDefaultSmsHandler = Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
        if (!SmsRoleActivationGate.canRequestRole(isDefaultSmsHandler = isDefaultSmsHandler)) return null
        return Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
            .putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
    }
}
