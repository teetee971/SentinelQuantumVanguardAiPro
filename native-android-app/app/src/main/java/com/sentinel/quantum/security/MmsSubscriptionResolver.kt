package com.sentinel.quantum.security

import android.content.Intent
import android.telephony.SubscriptionManager

/** Single fail-closed source for the SIM/subscription attached to an incoming WAP/MMS broadcast. */
internal object MmsSubscriptionResolver {
    fun resolve(intent: Intent): Int {
        val platform = intent.getIntExtra(
            SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,
            SubscriptionManager.INVALID_SUBSCRIPTION_ID
        )
        if (SubscriptionManager.isValidSubscriptionId(platform)) return platform

        // Older telephony stacks used this extra name for WAP push delivery.
        val legacy = intent.getIntExtra(
            "subscription",
            SubscriptionManager.INVALID_SUBSCRIPTION_ID
        )
        if (SubscriptionManager.isValidSubscriptionId(legacy)) return legacy

        val fallback = SubscriptionManager.getDefaultSmsSubscriptionId()
        return fallback.takeIf(SubscriptionManager::isValidSubscriptionId)
            ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID
    }
}
