package com.sentinel.quantum

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.os.Build
import android.provider.Telephony
import android.telecom.TelecomManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.PermissionChecker
import com.sentinel.quantum.security.AndroidRoleReadPolicy
import com.sentinel.quantum.security.CallScreeningActivationPolicy
import com.sentinel.quantum.security.MmsSafePreviewReadiness
import com.sentinel.quantum.security.SentinelCallNotificationHelper
import com.sentinel.quantum.security.SentinelMissedCallReceiver
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
        val callScreeningState = CallScreeningActivationPolicy.read(context)
        val notificationPermissionGranted =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                hasEffectivePermission(context, Manifest.permission.POST_NOTIFICATIONS)
        val notificationsGloballyEnabled =
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        val fullScreenIntentReady =
            SentinelCallNotificationHelper.isFullScreenIntentAllowed(context)

        return PhoneCoreSetupWizardStore.Facts(
            corePermissionsReady =
                hasEffectivePermission(context, Manifest.permission.CALL_PHONE) &&
                    hasEffectivePermission(context, Manifest.permission.READ_PHONE_STATE),
            dialerRoleHeld = holdsRole(context, RoleManager.ROLE_DIALER),
            dialerRoleAvailable = isRoleAvailable(context, RoleManager.ROLE_DIALER),
            callScreeningRoleHeld = callScreeningState == CallScreeningActivationPolicy.State.HELD,
            callScreeningRoleAvailable = callScreeningState != CallScreeningActivationPolicy.State.UNAVAILABLE,
            callLogPermissionGranted = hasEffectivePermission(context, Manifest.permission.READ_CALL_LOG),
            smsRoleHeld = smsRoleHeld,
            smsRoleAvailable = sms.smsRoleState != SmsActivationDiagnostics.SmsRoleState.UNAVAILABLE,
            smsRuntimePermissionsReady = smsRoleHeld &&
                SmsActivationDiagnostics.Blocker.SEND_SMS_PERMISSION_REQUIRED !in sms.blockers &&
                SmsActivationDiagnostics.Blocker.READ_SMS_PERMISSION_REQUIRED !in sms.blockers &&
                SmsActivationDiagnostics.Blocker.RECEIVE_SMS_PERMISSION_REQUIRED !in sms.blockers &&
                SmsActivationDiagnostics.Blocker.READ_PHONE_STATE_PERMISSION_REQUIRED !in sms.blockers,
            mmsPermissionsReady =
                hasEffectivePermission(context, Manifest.permission.RECEIVE_MMS) &&
                    hasEffectivePermission(context, Manifest.permission.RECEIVE_WAP_PUSH),
            mmsSafePreviewValidated = MmsSafePreviewReadiness.softwareValidated,
            notificationChannelsReady =
                notificationPermissionGranted &&
                    notificationsGloballyEnabled &&
                    SentinelCallNotificationHelper.isChannelEnabled(context) &&
                    SentinelMissedCallReceiver.isChannelEnabled(context) &&
                    SmsNotificationHelper.isChannelEnabled(context) &&
                    fullScreenIntentReady
        )
    }

    fun hasOperationalCarrierEnvironment(context: Context): Boolean {
        if (!hasEffectivePermission(context, Manifest.permission.READ_PHONE_STATE)) return false
        return try {
            context.getSystemService(TelecomManager::class.java)
                ?.callCapablePhoneAccounts.orEmpty().isNotEmpty() &&
                SmsActivationDiagnostics(context).snapshot().activeSubscriptionIds.isNotEmpty()
        } catch (_: SecurityException) {
            false
        } catch (_: RuntimeException) {
            false
        }
    }

    private fun hasEffectivePermission(context: Context, permission: String): Boolean =
        PermissionChecker.checkSelfPermission(context, permission) == PermissionChecker.PERMISSION_GRANTED

    private fun isRoleAvailable(context: Context, role: String): Boolean =
        AndroidRoleReadPolicy.readBoolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                role == RoleManager.ROLE_DIALER || role == RoleManager.ROLE_SMS
            } else {
                context.getSystemService(RoleManager::class.java)?.isRoleAvailable(role) == true
            }
        }

    private fun holdsRole(context: Context, role: String): Boolean =
        AndroidRoleReadPolicy.readBoolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                when (role) {
                    RoleManager.ROLE_DIALER ->
                        context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage ==
                            context.packageName
                    RoleManager.ROLE_SMS ->
                        Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
                    else -> false
                }
            } else {
                val manager = context.getSystemService(RoleManager::class.java) ?: return@readBoolean false
                manager.isRoleAvailable(role) && manager.isRoleHeld(role)
            }
        }
}
