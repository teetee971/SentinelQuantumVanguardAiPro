package com.sentinel.quantum.security

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat

/**
 * Single source of truth for the user-visible SMS activation state.
 *
 * This class never requests permissions and never changes the SMS role. It only reports what
 * Android has already granted so UI callers can remain fail-closed and ask for the minimum next
 * user action.
 */
class SmsActivationDiagnostics(private val context: Context) {
    enum class State { READY, LIMITED, LOCKED }

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
        val activeSubscriptionIds: List<Int>
    ) {
        /**
         * Sending does not require inbox/read or receive permissions. Keep this capability truth
         * separate from the aggregate activation state so the compose UI does not over-request
         * unrelated SMS permissions merely to enable an outbound message.
         *
         * An active subscription is still required because Sentinel's sender exposes an explicit
         * SIM selector and must not guess a subscription when telephony state is unavailable.
         */
        val canSend: Boolean
            get() = SMS_ROLE_REQUIRED !in blockers &&
                SEND_SMS_PERMISSION_REQUIRED !in blockers &&
                READ_PHONE_STATE_PERMISSION_REQUIRED !in blockers &&
                NO_ACTIVE_SIM !in blockers &&
                SUBSCRIPTION_LOOKUP_FAILED !in blockers &&
                activeSubscriptionIds.isNotEmpty()
    }

    fun snapshot(): Snapshot {
        val blockers = linkedSetOf<Blocker>()
        if (!holdsSmsRole()) blockers += Blocker.SMS_ROLE_REQUIRED
        if (!hasPermission(Manifest.permission.SEND_SMS)) {
            blockers += Blocker.SEND_SMS_PERMISSION_REQUIRED
        }
        if (!hasPermission(Manifest.permission.READ_SMS)) {
            blockers += Blocker.READ_SMS_PERMISSION_REQUIRED
        }
        if (!hasPermission(Manifest.permission.RECEIVE_SMS)) {
            blockers += Blocker.RECEIVE_SMS_PERMISSION_REQUIRED
        }

        val hasPhoneState = hasPermission(Manifest.permission.READ_PHONE_STATE)
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
        return Snapshot(state, blockers, subscriptions)
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun holdsSmsRole(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java) ?: return false
            roleManager.isRoleAvailable(RoleManager.ROLE_SMS) && roleManager.isRoleHeld(RoleManager.ROLE_SMS)
        } else {
            Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
        }
    }
}
