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
import com.sentinel.quantum.security.AndroidRoleReadPolicy
import com.sentinel.quantum.security.SentinelCallNotificationHelper
import com.sentinel.quantum.security.SmsActivationDiagnostics
import com.sentinel.quantum.security.SmsNotificationHelper

/**
 * Read-only runtime snapshot for Phone Core.
 *
 * Setup completeness and individual capabilities are deliberately separate: a macro wizard step
 * must not be reused as proof that one particular capability (for example call control) is ready.
 */
internal object PhoneCoreRuntimeFacts {
    data class CapabilityFacts(
        val callPermissionGranted: Boolean,
        val phoneStatePermissionGranted: Boolean,
        val contactsPermissionGranted: Boolean,
        val notificationPermissionGranted: Boolean,
        val dialerRoleHeld: Boolean,
        val dialerRoleAvailable: Boolean,
        val callScreeningRoleHeld: Boolean,
        val callScreeningRoleAvailable: Boolean,
        val callLogPermissionGranted: Boolean,
        val smsRoleHeld: Boolean,
        val smsRoleAvailable: Boolean,
        val smsRuntimePermissionsReady: Boolean,
        val mmsPermissionsReady: Boolean,
        val notificationChannelsReady: Boolean
    ) {
        /** Call placement + in-call ownership + call screening. Contacts/history are separate. */
        val callControlReady: Boolean
            get() = callPermissionGranted &&
                phoneStatePermissionGranted &&
                dialerRoleHeld &&
                callScreeningRoleHeld

        val contactsReady: Boolean get() = contactsPermissionGranted

        val callHistoryReady: Boolean
            get() = dialerRoleHeld && callLogPermissionGranted

        val messagingReady: Boolean
            get() = smsRoleHeld && smsRuntimePermissionsReady && mmsPermissionsReady
    }

    fun readCapabilities(context: Context): CapabilityFacts {
        val sms = SmsActivationDiagnostics(context).snapshot()
        val smsRoleHeld = sms.smsRoleState == SmsActivationDiagnostics.SmsRoleState.HELD
        val notificationPermissionGranted =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        val notificationsGloballyEnabled =
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        val fullScreenIntentReady =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
                context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() == true

        return CapabilityFacts(
            callPermissionGranted = hasPermission(context, Manifest.permission.CALL_PHONE),
            phoneStatePermissionGranted = hasPermission(context, Manifest.permission.READ_PHONE_STATE),
            contactsPermissionGranted = hasPermission(context, Manifest.permission.READ_CONTACTS),
            notificationPermissionGranted = notificationPermissionGranted,
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
                notificationsGloballyEnabled &&
                    SentinelCallNotificationHelper.isChannelEnabled(context) &&
                    SmsNotificationHelper.isChannelEnabled(context) &&
                    fullScreenIntentReady
        )
    }

    fun read(context: Context): PhoneCoreSetupWizardStore.Facts {
        val capability = readCapabilities(context)
        return PhoneCoreSetupWizardStore.Facts(
            // Preserve the existing setup contract: Contacts and notification runtime consent are
            // still required for complete Phone Core activation. Individual feature surfaces must
            // use CapabilityFacts instead of this aggregate.
            corePermissionsReady =
                capability.callPermissionGranted &&
                    capability.phoneStatePermissionGranted &&
                    capability.contactsPermissionGranted &&
                    capability.notificationPermissionGranted,
            dialerRoleHeld = capability.dialerRoleHeld,
            dialerRoleAvailable = capability.dialerRoleAvailable,
            callScreeningRoleHeld = capability.callScreeningRoleHeld,
            callScreeningRoleAvailable = capability.callScreeningRoleAvailable,
            callLogPermissionGranted = capability.callLogPermissionGranted,
            smsRoleHeld = capability.smsRoleHeld,
            smsRoleAvailable = capability.smsRoleAvailable,
            smsRuntimePermissionsReady = capability.smsRuntimePermissionsReady,
            mmsPermissionsReady = capability.mmsPermissionsReady,
            notificationChannelsReady = capability.notificationChannelsReady
        )
    }

    fun hasOperationalCarrierEnvironment(context: Context): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) !=
            PackageManager.PERMISSION_GRANTED) return false
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

    private fun hasPermission(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

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
