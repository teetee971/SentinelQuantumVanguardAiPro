package com.sentinel.quantum.security

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

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
        if (!manager.isRoleAvailable(RoleManager.ROLE_SMS) || manager.isRoleHeld(RoleManager.ROLE_SMS)) {
            return null
        }
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
        if (SmsActivationDiagnostics.Blocker.READ_PHONE_STATE_PERMISSION_REQUIRED in snapshot.blockers) {
            add(Manifest.permission.READ_PHONE_STATE)
        }
        }.toTypedArray()
    }

    /**
     * Android 9 and earlier have no RoleManager request contract. Open the system default-app
     * settings rather than trying to mutate the default SMS package programmatically.
     */
    fun legacyDefaultAppsIntent(): Intent? =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        else null
}
