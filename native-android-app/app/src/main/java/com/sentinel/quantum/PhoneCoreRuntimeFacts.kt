package com.sentinel.quantum

import android.Manifest
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import android.telecom.TelecomManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sentinel.quantum.security.SentinelCallNotificationHelper
import com.sentinel.quantum.security.SmsActivationDiagnostics
import com.sentinel.quantum.security.SmsNotificationHelper

/**
 * Read-only runtime snapshot for the Phone Core setup contract.
 *
 * This collector never requests a role or permission and never trusts persisted wizard completion.
 * It exists so every UI surface derives setup readiness from the same Android facts.
 */
internal object PhoneCoreRuntimeFacts {
    fun read(context: Context): PhoneCoreSetupWizardStore.Facts {
        val sms = SmsActivationDiagnostics(context).snapshot()
        val smsRoleHeld = sms.smsRoleState == SmsActivationDiagnostics.SmsRoleState.HELD
        val notificationPermissionReady =
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)) &&
                NotificationManagerCompat.from(context).areNotificationsEnabled()
        val fullScreenIntentReady =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
                context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() == true

        return PhoneCoreSetupWizardStore.Facts(
            corePermissionsReady =
                hasPermission(context, Manifest.permission.CALL_PHONE) &&
                    hasPermission(context, Manifest.permission.READ_PHONE_STATE) &&
                    hasPermission(context, Manifest.permission.READ_CONTACTS) &&
                    notificationPermissionReady,
            dialerRoleHeld = holdsRole(context, RoleManager.ROLE_DIALER),
            dialerRoleAvailable = isRoleAvailable(context, RoleManager.ROLE_DIALER),
            callScreeningRoleHeld =
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    holdsRole(context, RoleManager.ROLE_CALL_SCREENING),
            callScreeningRoleAvailable =
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    isRoleAvailable(context, RoleManager.ROLE_CALL_SCREENING),
            callLogPermissionGranted = hasPermission(context, Manifest.permission.READ_CALL_LOG),
            smsRoleHeld = smsRoleHeld,
            smsRoleAvailable = sms.smsRoleState != SmsActivationDiagnostics.SmsRoleState.UNAVAILABLE,
            smsRuntimePermissionsReady = smsRoleHeld &&
                SmsActivationDiagnostics.Blocker.SEND_SMS_PERMISSION_REQUIRED !in sms.blockers &&
                SmsActivationDiagnostics.Blocker.READ_SMS_PERMISSION_REQUIRED !in sms.blockers &&
                SmsActivationDiagnostics.Blocker.RECEIVE_SMS_PERMISSION_REQUIRED !in sms.blockers &&
                SmsActivationDiagnostics.Blocker.READ_PHONE_STATE_PERMISSION_REQUIRED !in sms.blockers,
            mmsPermissionsReady =
                hasPermission(context, Manifest.permission.RECEIVE_MMS) &&
                    hasPermission(context, Manifest.permission.RECEIVE_WAP_PUSH),
            notificationChannelsReady =
                SentinelCallNotificationHelper.isChannelEnabled(context) &&
                    SmsNotificationHelper.isChannelEnabled(context) &&
                    fullScreenIntentReady
        )
    }

    private fun hasPermission(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun isRoleAvailable(context: Context, role: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return role == RoleManager.ROLE_DIALER || role == RoleManager.ROLE_SMS
        }
        return context.getSystemService(RoleManager::class.java)?.isRoleAvailable(role) == true
    }

    private fun holdsRole(context: Context, role: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return when (role) {
                RoleManager.ROLE_DIALER ->
                    context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage ==
                        context.packageName
                RoleManager.ROLE_SMS -> Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
                else -> false
            }
        }
        val manager = context.getSystemService(RoleManager::class.java) ?: return false
        return manager.isRoleAvailable(role) && manager.isRoleHeld(role)
    }
}
