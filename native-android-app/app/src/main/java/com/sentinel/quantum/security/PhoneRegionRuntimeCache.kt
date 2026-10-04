package com.sentinel.quantum.security

import android.content.Context
import android.telephony.SubscriptionManager
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Process-local fail-open cache for an unambiguous active-subscription ISO region. */
internal object PhoneRegionRuntimeCache {
    @Volatile private var regionIso: String? = null
    @Volatile private var started = false
    @Volatile private var subscriptionListener: SubscriptionManager.OnSubscriptionsChangedListener? = null

    // Subscription changes are low-frequency but can arrive in bursts during radio/SIM transitions.
    // Keep one worker and at most one pending refresh; stale queued observations are discarded.
    private val executor = ThreadPoolExecutor(
        1,
        1,
        30L,
        TimeUnit.SECONDS,
        ArrayBlockingQueue(1),
        { runnable -> Thread(runnable, "sentinel-phone-region-refresh").apply { isDaemon = true } },
        ThreadPoolExecutor.DiscardOldestPolicy()
    ).apply {
        allowCoreThreadTimeOut(true)
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
