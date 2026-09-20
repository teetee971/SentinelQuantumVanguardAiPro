package com.sentinel.quantum.security

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat

/**
 * Refreshable source for the SMS composer SIM selector.
 *
 * Unlike a one-shot Compose remember block, callers may invoke [load] after permission/role
 * activity results or lifecycle resume. The source is read-only and fail-closed: denied access or
 * provider failures never manufacture an active subscription.
 */
class SmsSubscriptionState(private val context: Context) {
    sealed interface Result {
        data class Available(val subscriptions: List<SubscriptionInfo>) : Result
        data object PermissionRequired : Result
        data object LookupFailed : Result
    }

    fun load(): Result {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.PermissionRequired
        }

        return try {
            val subscriptions = context.getSystemService(SubscriptionManager::class.java)
                ?.activeSubscriptionInfoList
                .orEmpty()
                .filter { it.subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID }
                .distinctBy { it.subscriptionId }
            Result.Available(subscriptions)
        } catch (_: SecurityException) {
            Result.LookupFailed
        } catch (_: RuntimeException) {
            Result.LookupFailed
        }
    }

    companion object {
        /** Keep an explicit user choice only while that SIM remains active. */
        fun reconcileSelection(selectedId: Int?, activeIds: List<Int>): Int? = when {
            selectedId != null && selectedId in activeIds -> selectedId
            activeIds.size == 1 -> activeIds.single()
            else -> null
        }
    }
}
