package com.sentinel.quantum

import android.Manifest
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.TelephonyManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.PermissionChecker
import com.sentinel.quantum.security.AndroidRoleReadPolicy
import com.sentinel.quantum.security.CallScreeningActivationPolicy
import com.sentinel.quantum.security.SentinelCallNotificationHelper
import com.sentinel.quantum.security.SentinelMissedCallReceiver
import com.sentinel.quantum.security.SmsNotificationHelper

/**
 * Read-only, local and PII-free Android activation snapshot.
 *
 * No phone number, SIM/subscription identifier, account handle, contact, hardware identifier,
 * install source, network identifier or persisted wizard result is collected.
 */
internal object PhoneCoreLocalDiagnostics {
    data class Snapshot(
        val sdkInt: Int,
        val androidRelease: String,
        val appVersionName: String,
        val appVersionCode: Long,
        val voiceCapable: Boolean,
        val dialerRoleAvailable: Boolean,
        val dialerRoleHeld: Boolean,
        val callScreeningRoleAvailable: Boolean,
        val callScreeningRoleHeld: Boolean,
        val smsRoleAvailable: Boolean,
        val smsRoleHeld: Boolean,
        val callPermission: Boolean,
        val phoneStatePermission: Boolean,
        val contactsPermission: Boolean,
        val callLogPermission: Boolean,
        val sendSmsPermission: Boolean,
        val readSmsPermission: Boolean,
        val receiveSmsPermission: Boolean,
        val receiveMmsPermission: Boolean,
        val receiveWapPushPermission: Boolean,
        val postNotificationsPermission: Boolean,
        val notificationsGloballyEnabled: Boolean,
        val callNotificationChannelEnabled: Boolean,
        val missedCallNotificationChannelEnabled: Boolean,
        val smsNotificationChannelEnabled: Boolean,
        val fullScreenIntentAllowed: Boolean
    )

    fun read(context: Context): Snapshot {
        val roleManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            AndroidRoleReadPolicy.readOrNull {
                context.getSystemService(RoleManager::class.java)
            }
        } else null
        fun roleAvailable(role: String): Boolean =
            AndroidRoleReadPolicy.readBoolean {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    role == RoleManager.ROLE_DIALER || role == RoleManager.ROLE_SMS
                } else {
                    roleManager?.isRoleAvailable(role) == true
                }
            }
        fun roleHeld(role: String): Boolean =
            AndroidRoleReadPolicy.readBoolean {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    when (role) {
                        RoleManager.ROLE_DIALER ->
                            context.getSystemService(android.telecom.TelecomManager::class.java)
                                ?.defaultDialerPackage == context.packageName
                        RoleManager.ROLE_SMS ->
                            android.provider.Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
                        else -> false
                    }
                } else {
                    roleAvailable(role) && roleManager?.isRoleHeld(role) == true
                }
            }
        fun granted(permission: String): Boolean =
            PermissionChecker.checkSelfPermission(context, permission) == PermissionChecker.PERMISSION_GRANTED

        val callScreeningState = CallScreeningActivationPolicy.read(context)
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }

        return Snapshot(
            sdkInt = Build.VERSION.SDK_INT,
            androidRelease = Build.VERSION.RELEASE.orEmpty(),
            appVersionName = info.versionName.orEmpty(),
            appVersionCode = versionCode,
            voiceCapable = context.getSystemService(TelephonyManager::class.java)?.isVoiceCapable == true,
            dialerRoleAvailable = roleAvailable(RoleManager.ROLE_DIALER),
            dialerRoleHeld = roleHeld(RoleManager.ROLE_DIALER),
            callScreeningRoleAvailable = callScreeningState != CallScreeningActivationPolicy.State.UNAVAILABLE,
            callScreeningRoleHeld = callScreeningState == CallScreeningActivationPolicy.State.HELD,
            smsRoleAvailable = roleAvailable(RoleManager.ROLE_SMS),
            smsRoleHeld = roleHeld(RoleManager.ROLE_SMS),
            callPermission = granted(Manifest.permission.CALL_PHONE),
            phoneStatePermission = granted(Manifest.permission.READ_PHONE_STATE),
            contactsPermission = granted(Manifest.permission.READ_CONTACTS),
            callLogPermission = granted(Manifest.permission.READ_CALL_LOG),
            sendSmsPermission = granted(Manifest.permission.SEND_SMS),
            readSmsPermission = granted(Manifest.permission.READ_SMS),
            receiveSmsPermission = granted(Manifest.permission.RECEIVE_SMS),
            receiveMmsPermission = granted(Manifest.permission.RECEIVE_MMS),
            receiveWapPushPermission = granted(Manifest.permission.RECEIVE_WAP_PUSH),
            postNotificationsPermission =
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    granted(Manifest.permission.POST_NOTIFICATIONS),
            notificationsGloballyEnabled =
                NotificationManagerCompat.from(context).areNotificationsEnabled(),
            callNotificationChannelEnabled =
                SentinelCallNotificationHelper.isChannelEnabled(context),
            missedCallNotificationChannelEnabled =
                SentinelMissedCallReceiver.isChannelEnabled(context),
            smsNotificationChannelEnabled =
                SmsNotificationHelper.isChannelEnabled(context),
            fullScreenIntentAllowed =
                Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
                    context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() == true
        )
    }
}
