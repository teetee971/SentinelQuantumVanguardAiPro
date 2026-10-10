package com.sentinel.quantum.security

import android.Manifest
import android.content.Context
import android.content.Intent
import android.telephony.SubscriptionManager
import androidx.core.content.PermissionChecker

/**
 * Single fail-closed source for the SIM/subscription attached to an incoming WAP/MMS broadcast.
 *
 * Modern AOSP telephony broadcasts carry the subscription id explicitly. OEM/legacy stacks may
 * expose only a slot. We never guess a multi-SIM message from the user's default SMS subscription:
 * if explicit identity is unavailable, resolution is allowed only when a slot maps uniquely or
 * exactly one active subscription exists.
 */
internal object MmsSubscriptionResolver {
    fun resolve(context: Context, intent: Intent): Int {
        explicitSubscriptionId(intent, SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX)?.let {
            return it
        }
        // Older telephony stacks and AOSP internal PhoneConstants use this key.
        explicitSubscriptionId(intent, LEGACY_SUBSCRIPTION_KEY)?.let {
            return it
        }

        val active = activeSubscriptions(context)
        val slot = explicitSlotIndex(intent)
        if (slot != null) {
            val candidates = active.asSequence()
                .filter { it.simSlotIndex == slot }
                .map { it.subscriptionId }
                .filter(::isValidSubscriptionId)
                .distinct()
                .toList()
            return candidates.singleOrNull() ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID
        }

        return active.asSequence()
            .map { it.subscriptionId }
            .filter(::isValidSubscriptionId)
            .distinct()
            .toList()
            .singleOrNull()
            ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID
    }

    private fun explicitSubscriptionId(intent: Intent, key: String): Int? =
        subscriptionIdFromNumber(numericExtra(intent, key))
            .takeIf(::isValidSubscriptionId)

    private fun explicitSlotIndex(intent: Intent): Int? {
        val platform = slotIndexFromNumber(numericExtra(intent, SubscriptionManager.EXTRA_SLOT_INDEX))
        if (platform != null) return platform
        return slotIndexFromNumber(numericExtra(intent, LEGACY_SLOT_KEY))
    }

    private fun activeSubscriptions(context: Context): List<android.telephony.SubscriptionInfo> {
        if (
            PermissionChecker.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) !=
            PermissionChecker.PERMISSION_GRANTED
        ) {
            return emptyList()
        }
        return runCatching {
            context.getSystemService(SubscriptionManager::class.java)
                ?.activeSubscriptionInfoList
                .orEmpty()
                .filter { isValidSubscriptionId(it.subscriptionId) }
        }.getOrDefault(emptyList())
    }

    @Suppress("DEPRECATION")
    private fun numericExtra(intent: Intent, key: String): Number? = runCatching {
        intent.extras?.get(key) as? Number
    }.getOrNull()

    /**
     * API-24-compatible subscription-id contract.
     *
     * SubscriptionManager.isValidSubscriptionId() was added only in API 29. Android reserves
     * negative values for invalid/default/sentinel subscription identifiers, while real
     * subscription ids are non-negative. Keeping this check local avoids accidentally raising the
     * Phone Core minSdk just to validate an integer.
     */
    internal fun isValidSubscriptionId(value: Int): Boolean = value >= 0

    internal fun subscriptionIdFromNumber(value: Number?): Int {
        val raw = value?.toLong() ?: return SubscriptionManager.INVALID_SUBSCRIPTION_ID
        if (raw !in 0L..Int.MAX_VALUE.toLong()) return SubscriptionManager.INVALID_SUBSCRIPTION_ID
        return raw.toInt()
    }

    internal fun slotIndexFromNumber(value: Number?): Int? {
        val raw = value?.toLong() ?: return null
        if (raw !in 0L..Int.MAX_VALUE.toLong()) return null
        return raw.toInt()
    }

    internal fun explicitSlotIndexForRecovery(intent: Intent): Int? = explicitSlotIndex(intent)

    internal fun explicitSubscriptionIdForRecovery(intent: Intent): Int? =
        explicitSubscriptionId(intent, SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX)
            ?: explicitSubscriptionId(intent, LEGACY_SUBSCRIPTION_KEY)

    private const val LEGACY_SUBSCRIPTION_KEY = "subscription"
    private const val LEGACY_SLOT_KEY = "slot"
}
