package com.sentinel.quantum.security

import android.content.Context
import android.telephony.SubscriptionManager
import java.util.concurrent.Executors

/** Process-local fail-open cache for an unambiguous active-subscription ISO region. */
internal object PhoneRegionRuntimeCache {
    @Volatile private var regionIso: String? = null
    @Volatile private var started = false
    @Volatile private var subscriptionListener: SubscriptionManager.OnSubscriptionsChangedListener? = null
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "sentinel-phone-region-refresh").apply { isDaemon = true }
    }

    fun start(context: Context) {
        val appContext = context.applicationContext
        synchronized(this) {
            if (!started) {
                started = true
                appContext.getSystemService(SubscriptionManager::class.java)?.let { manager ->
                    val listener = object : SubscriptionManager.OnSubscriptionsChangedListener() {
                        override fun onSubscriptionsChanged() {
                            refresh(appContext)
                        }
                    }
                    subscriptionListener = listener
                    @Suppress("DEPRECATION")
                    runCatching { manager.addOnSubscriptionsChangedListener(listener) }
                }
            }
        }
        refresh(appContext)
    }

    fun currentRegionIso(): String? = regionIso

    private fun refresh(context: Context) {
        // Clear first: stale country semantics are never usable while subscriptions are changing.
        regionIso = null
        runCatching {
            executor.execute {
                regionIso = runCatching {
                    AndroidPhoneNumberCanonicalizer(context).observedRegionIso()
                }.getOrNull()
            }
        }
    }
}
