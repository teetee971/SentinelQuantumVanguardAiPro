package com.sentinel.quantum

import android.app.Application
import com.sentinel.quantum.security.CallBlocklistStore
import com.sentinel.quantum.security.MmsSendCleanupWorker
import com.sentinel.quantum.security.SentinelSmsStatusReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Process-level services and lifecycle-safe maintenance for Sentinel.
 *
 * [onCreate] intentionally performs no screening preparation. Android creates the Application
 * before CallScreeningService, so disk, WorkManager and AndroidKeyStore work here would extend the
 * cold-start window before the mandatory Telecom response. The screening service invokes
 * [runPostResponseMaintenance] only from its bounded post-response worker. Until that first
 * response completes, exact, prefix and signed rules may fail open while the process cache is
 * cold; the same local rules are then available to subsequent callbacks.
 */
class SentinelApplication : Application() {
    private val voiceTeardownScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val startupMaintenanceCompleted = AtomicBoolean(false)

    fun launchVoiceTeardown(block: suspend () -> Unit) {
        voiceTeardownScope.launch {
            block()
        }
    }

    override fun onCreate() {
        super.onCreate()
    }

    /**
     * Performs bounded local repair and cache warm-up after Telecom has received its response.
     *
     * CallScreeningService owns the only automatic caller. Keeping this synchronous is safe
     * because the caller is already the bounded post-response worker, never onScreenCall().
     */
    internal fun runPostResponseMaintenance() {
        if (!startupMaintenanceCompleted.compareAndSet(false, true)) return

        SentinelSmsStatusReceiver.queueProviderRepair(applicationContext)
        runCatching { MmsSendCleanupWorker.scheduleStartupRecovery(applicationContext) }
        runCatching {
            val store = CallBlocklistStore(applicationContext)
            val screeningSnapshot = store.prepareScreeningSnapshot()
            if (screeningSnapshot.blockedNumberHashes.isNotEmpty()) {
                store.prepareFingerprintKeys()
            }
        }
    }
}
