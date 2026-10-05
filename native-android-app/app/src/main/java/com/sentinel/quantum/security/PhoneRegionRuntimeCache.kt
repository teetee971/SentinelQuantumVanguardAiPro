package com.sentinel.quantum.security

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.telephony.SubscriptionManager
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Process-local fail-open cache for an unambiguous active-subscription ISO region. */
internal object PhoneRegionRuntimeCache {
    @Volatile private var regionIso: String? = null
    @Volatile private var started = false
    @Volatile private var lifecycleCallbacksRegistered = false
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
        registerLifecycleRetry(appContext)
        val trackingReady = synchronized(this) {
            if (!started) {
                val manager = appContext.getSystemService(SubscriptionManager::class.java)
                if (manager == null) {
                    regionIso = null
                    false
                } else {
                    val listener = object : SubscriptionManager.OnSubscriptionsChangedListener() {
                        override fun onSubscriptionsChanged() {
                            refresh(appContext, invalidateFirst = true)
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
                        // after SIM/subscription changes. Remain region-unknown and retry after an
                        // Activity resumes (for example immediately after READ_PHONE_STATE grant).
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
            // Do not clear a still-valid observation merely because an Activity resumed. The SIM
            // listener owns invalidation. A null cache is retried here so a permission granted
            // during onboarding becomes usable without a process restart.
            if (regionIso == null) refresh(appContext, invalidateFirst = false)
        } else {
            regionIso = null
        }
    }

    fun currentRegionIso(): String? = regionIso

    private fun registerLifecycleRetry(context: Context) {
        val application = context as? Application ?: return
        synchronized(this) {
            if (lifecycleCallbacksRegistered) return
            application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) {
                    if (regionIso == null || !started) start(application)
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
                override fun onActivityStarted(activity: Activity) = Unit
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivityStopped(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            })
            lifecycleCallbacksRegistered = true
        }
    }

    private fun refresh(context: Context, invalidateFirst: Boolean) {
        // Subscription changes invalidate synchronously. Permission/lifecycle retries do not erase
        // an already valid observation and always execute off the screening callback thread.
        if (invalidateFirst) regionIso = null
        runCatching {
            executor.execute {
                regionIso = runCatching {
                    AndroidPhoneNumberCanonicalizer(context).observedRegionIso()
                }.getOrNull()
            }
        }
    }
}
