package com.sentinel.quantum.security

import android.content.Context
import java.util.concurrent.Executors

/** Process-wide screening cache warm-up started as early as Application.onCreate(). */
object CallScreeningWarmup {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "sentinel-call-screening-warmup").apply { isDaemon = true }
    }

    private val coordinator = CallScreeningWarmupCoordinator(
        loader = { checkNotNull(applicationContext).let(::loadRules) },
        dispatch = executor::execute
    )

    @Volatile private var applicationContext: Context? = null

    fun start(context: Context) {
        if (applicationContext == null) {
            synchronized(this) {
                if (applicationContext == null) applicationContext = context.applicationContext
            }
        }
        coordinator.start()
    }

    /**
     * Screening may wait only for this bounded budget. If storage or AndroidKeyStore is slow,
     * callers fail open rather than violating Telecom's response deadline.
     */
    fun awaitReady(timeoutMs: Long): Boolean = coordinator.awaitReady(timeoutMs)

    internal fun state(): CallScreeningWarmupCoordinator.State = coordinator.state()

    private fun loadRules(context: Context) {
        val store = CallBlocklistStore(context)
        val snapshot = store.prepareScreeningSnapshot()
        if (snapshot.blockedNumberHashes.isNotEmpty()) {
            store.prepareFingerprintKeys()
        }
    }
}
