package com.sentinel.quantum.security

import android.content.Context
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Loads screening rules, HMAC keys and SIM regions off the Telecom callback.
 *
 * [awaitForScreening] never opens disk or AndroidKeyStore. It waits at most
 * [CALLBACK_WAIT_BUDGET_MS], far under the framework's 5 second incoming-call budget.
 * A slower disk or keystore leaves that call fail-open; the same load still finishes
 * for the next call. Missing keys are not generated here.
 */
internal object ScreeningProcessWarmup {
    const val CALLBACK_WAIT_BUDGET_MS = 250L

    private val started = AtomicBoolean(false)
    private val gate = Any()
    private var rulesLatch = CountDownLatch(1)
    private var keysLatch = CountDownLatch(1)
    private var regionsLatch = CountDownLatch(1)
    @Volatile private var worker: Thread? = null

    fun schedule(context: Context) {
        val app = context.applicationContext
        start { load(app) }
    }

    /** Test seam. The loader runs off the caller thread; latches open after it returns. */
    internal fun scheduleLoader(loader: () -> Unit) {
        start(loader)
    }

    fun awaitForScreening(budgetMs: Long = CALLBACK_WAIT_BUDGET_MS): Boolean {
        val rules = rulesLatch
        val keys = keysLatch
        val regions = regionsLatch
        val startedAt = System.nanoTime()
        if (!rules.await(budgetMs, TimeUnit.MILLISECONDS)) return false
        val afterRules = elapsedMs(startedAt)
        if (CallBlocklistStore.cachedSnapshotForScreening().blockedNumberHashes.isNotEmpty()) {
            val remaining = budgetMs - afterRules
            if (remaining > 0) keys.await(remaining, TimeUnit.MILLISECONDS)
        }
        val remainingRegions = budgetMs - elapsedMs(startedAt)
        if (remainingRegions > 0) regions.await(remainingRegions, TimeUnit.MILLISECONDS)
        return true
    }

    internal fun resetForTests() {
        worker?.interrupt()
        worker = null
        started.set(false)
        synchronized(gate) {
            rulesLatch = CountDownLatch(1)
            keysLatch = CountDownLatch(1)
            regionsLatch = CountDownLatch(1)
        }
    }

    private fun start(work: () -> Unit) {
        if (!started.compareAndSet(false, true)) return
        val rules: CountDownLatch
        val keys: CountDownLatch
        val regions: CountDownLatch
        synchronized(gate) {
            rules = rulesLatch
            keys = keysLatch
            regions = regionsLatch
        }
        val thread = Thread({
            try {
                work()
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } finally {
                rules.countDown()
                keys.countDown()
                regions.countDown()
            }
        }, "sentinel-screening-warmup")
        thread.isDaemon = true
        worker = thread
        thread.start()
    }

    private fun load(app: Context) {
        runCatching {
            CallBlocklistStore(app).prepareScreeningSnapshot()
        }
        synchronized(gate) { rulesLatch.countDown() }
        runCatching {
            val store = CallBlocklistStore(app)
            if (store.cachedScreeningSnapshot().blockedNumberHashes.isNotEmpty()) {
                store.prepareFingerprintKeys()
            }
        }
        synchronized(gate) { keysLatch.countDown() }
        runCatching { InternationalNumberParser.warm() }
        runCatching { ScreeningHomeRegion.capture(app) }
        synchronized(gate) { regionsLatch.countDown() }
    }

    private fun elapsedMs(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000L
}
