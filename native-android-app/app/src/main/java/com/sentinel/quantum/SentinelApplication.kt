package com.sentinel.quantum

import android.app.Application
import com.sentinel.quantum.security.CallBlocklistStore
import com.sentinel.quantum.security.MmsSendCleanupWorker
import com.sentinel.quantum.security.SentinelSmsStatusReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Process-level services and lifecycle-safe maintenance for Sentinel.
 *
 * [onCreate] intentionally performs no screening preparation. Android creates the Application
 * before CallScreeningService, so disk, WorkManager and AndroidKeyStore work here would extend the
 * cold-start window before the mandatory Telecom response. The normal visible app lifecycle calls
 * [runNonCriticalStartupMaintenance] later, off the main thread. Until then, exact matching may
 * fail open when the process-local fingerprint cache is cold; prefix and signed-prefix rules
 * remain available.
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
     * Runs non-critical repair and cache warm-up only while a visible Activity is resumed.
     *
     * This method is deliberately absent from [onCreate]. The screening service is allowed to
     * answer from its in-memory cache while this work is unavailable or still pending. Every
     * operation is bounded to existing local maintenance APIs and executes on Dispatchers.IO.
     */
    suspend fun runNonCriticalStartupMaintenance() {
        if (startupMaintenanceCompleted.get()) return

        withContext(Dispatchers.IO) {
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
        startupMaintenanceCompleted.set(true)
    }
}
