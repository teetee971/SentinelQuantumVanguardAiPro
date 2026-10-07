package com.sentinel.quantum.security

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker

/**
 * Single source of truth for the user-visible SMS activation state.
 *
 * This class never requests permissions and never changes the SMS role. It only reports what
 * Android has already granted so UI callers can remain fail-closed and ask for the minimum next
 * user action. Permission reads require both the raw runtime grant and the associated AppOp-aware
 * PermissionChecker result when Android defines one, so neither a revoked runtime permission nor a
 * platform-level operation denial can be presented as an actionable send state.
 */
class SmsActivationDiagnostics(private val context: Context) {
    enum class State { READY, LIMITED, LOCKED }
    enum class SmsRoleState { HELD, AVAILABLE_NOT_HELD, UNAVAILABLE }

    enum class Blocker {
        SMS_ROLE_REQUIRED,
        SEND_SMS_PERMISSION_REQUIRED,
        READ_SMS_PERMISSION_REQUIRED,
        RECEIVE_SMS_PERMISSION_REQUIRED,
        READ_PHONE_STATE_PERMISSION_REQUIRED,
        NO_ACTIVE_SIM,
        SUBSCRIPTION_LOOKUP_FAILED
    }

    data class Snapshot(
        val state: State,
        val blockers: Set<Blocker>,
        val activeSubscriptionIds: List<Int>,
        val smsRoleState: SmsRoleState = if (Blocker.SMS_ROLE_REQUIRED in blockers) SmsRoleState.AVAILABLE_NOT_HELD else SmsRoleState.HELD
    ) {
        /**
         * Sending does not require inbox/read or receive permissions. Keep this capability truth
         * separate from the aggregate activation state so the compose UI does not over-request
         * unrelated SMS permissions merely to enable an outbound message.
         *
         * An active subscription is still required because Sentinel's sender exposes an explicit
         * SIM selector and must not guess a subscription when telephony state is unavailable.
         */
        val needsSendRuntimePermissions: Boolean
            get() = smsRoleState == SmsRoleState.HELD &&
                (Blocker.SEND_SMS_PERMISSION_REQUIRED in blockers ||
                    Blocker.READ_PHONE_STATE_PERMISSION_REQUIRED in blockers)

        val canSend: Boolean
            get() = smsRoleState == SmsRoleState.HELD &&
                Blocker.SEND_SMS_PERMISSION_REQUIRED !in blockers &&
                Blocker.READ_PHONE_STATE_PERMISSION_REQUIRED !in blockers &&
                Blocker.NO_ACTIVE_SIM !in blockers &&
                Blocker.SUBSCRIPTION_LOOKUP_FAILED !in blockers &&
                activeSubscriptionIds.isNotEmpty()
    }

    fun snapshot(): Snapshot {
        val blockers = linkedSetOf<Blocker>()
        val roleState = smsRoleState()
        if (roleState != SmsRoleState.HELD) blockers += Blocker.SMS_ROLE_REQUIRED
        if (!hasEffectivePermission(Manifest.permission.SEND_SMS)) {
            blockers += Blocker.SEND_SMS_PERMISSION_REQUIRED
        }
        if (!hasEffectivePermission(Manifest.permission.READ_SMS)) {
            blockers += Blocker.READ_SMS_PERMISSION_REQUIRED
        }
        if (!hasEffectivePermission(Manifest.permission.RECEIVE_SMS)) {
            blockers += Blocker.RECEIVE_SMS_PERMISSION_REQUIRED
        }

        val hasPhoneState = hasEffectivePermission(Manifest.permission.READ_PHONE_STATE)
        if (!hasPhoneState) blockers += Blocker.READ_PHONE_STATE_PERMISSION_REQUIRED

        var lookupFailed = false
        val subscriptions = if (hasPhoneState) {
            try {
                context.getSystemService(SubscriptionManager::class.java)
                    ?.activeSubscriptionInfoList
                    .orEmpty()
                    .map { it.subscriptionId }
                    .filter { it != SubscriptionManager.INVALID_SUBSCRIPTION_ID }
                    .distinct()
            } catch (_: SecurityException) {
                lookupFailed = true
                emptyList()
            } catch (_: RuntimeException) {
                lookupFailed = true
                emptyList()
            }
        } else emptyList()

        if (lookupFailed) blockers += Blocker.SUBSCRIPTION_LOOKUP_FAILED
        else if (hasPhoneState && subscriptions.isEmpty()) blockers += Blocker.NO_ACTIVE_SIM

        val state = when {
            blockers.isEmpty() -> State.READY
            Blocker.SMS_ROLE_REQUIRED in blockers ||
                Blocker.SEND_SMS_PERMISSION_REQUIRED in blockers ||
                Blocker.READ_SMS_PERMISSION_REQUIRED in blockers ||
                Blocker.RECEIVE_SMS_PERMISSION_REQUIRED in blockers -> State.LOCKED
            else -> State.LIMITED
        }
        return Snapshot(state, blockers, subscriptions, roleState)
    }

    private fun hasEffectivePermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED &&
            PermissionChecker.checkSelfPermission(context, permission) == PermissionChecker.PERMISSION_GRANTED

    private fun smsRoleState(): SmsRoleState = context.readSmsRoleStateFailClosed()

}

/**
 * Shared fail-closed boundary for every SMS-role read.
 *
 * Role/default-app queries are framework diagnostics, not authorization proofs. Vendor builds and
 * transient framework states can throw at this boundary; callers must degrade to UNAVAILABLE.
 */
internal object SmsRoleReadPolicy {
    fun read(
        block: () -> SmsActivationDiagnostics.SmsRoleState
    ): SmsActivationDiagnostics.SmsRoleState =
        try {
            block()
        } catch (_: SecurityException) {
            SmsActivationDiagnostics.SmsRoleState.UNAVAILABLE
        } catch (_: RuntimeException) {
            SmsActivationDiagnostics.SmsRoleState.UNAVAILABLE
        }
}

/** Reads the platform SMS role/default-app state through the shared fail-closed boundary. */
internal fun Context.readSmsRoleStateFailClosed(): SmsActivationDiagnostics.SmsRoleState =
    SmsRoleReadPolicy.read {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager == null) {
                SmsActivationDiagnostics.SmsRoleState.UNAVAILABLE
            } else {
                when {
                    !roleManager.isRoleAvailable(RoleManager.ROLE_SMS) ->
                        SmsActivationDiagnostics.SmsRoleState.UNAVAILABLE
                    roleManager.isRoleHeld(RoleManager.ROLE_SMS) ->
                        SmsActivationDiagnostics.SmsRoleState.HELD
                    else ->
                        SmsActivationDiagnostics.SmsRoleState.AVAILABLE_NOT_HELD
                }
            }
        } else if (Telephony.Sms.getDefaultSmsPackage(this) == packageName) {
            SmsActivationDiagnostics.SmsRoleState.HELD
        } else {
            SmsActivationDiagnostics.SmsRoleState.AVAILABLE_NOT_HELD
        }
    }