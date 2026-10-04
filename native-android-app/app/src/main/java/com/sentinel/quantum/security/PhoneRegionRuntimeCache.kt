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
        val trackingReady = synchronized(this) {
            if (!started) {
                val manager = appContext.getSystemService(SubscriptionManager::class.java)
                if (manager == null) {
                    regionIso = null
                    false
                } else {
                    val listener = object : SubscriptionManager.OnSubscriptionsChangedListener() {
                        override fun onSubscriptionsChanged() {
                            refresh(appContext)
                        }
                    }
                    @Suppress("DEPRECATION")
                    val registered = runCatching {
                        manager.addOnSubscriptionsChangedListener(listener)
                        true
                    }.getOrDefault(false)
                    if (registered) {
                        subscriptionListener = listener
                        started = true
                        true
                    } else {
                        // A one-shot region observation is unsafe if Sentinel cannot invalidate it
                        // after SIM/subscription changes. Remain region-unknown and allow retry on a
                        // later start() call instead of keeping stale country semantics.
                        subscriptionListener = null
                        regionIso = null
                        started = false
                        false
                    }
                }
            } else {
                subscriptionListener != null
            }
        }

        if (trackingReady) {
            refresh(appContext)
        } else {
            regionIso = null
        }
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
