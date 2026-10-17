package com.sentinel.quantum

import android.app.Application
import com.sentinel.quantum.security.CallBlocklistStore
import com.sentinel.quantum.security.CallScreeningWarmup
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
 * Android creates the Application before CallScreeningService. Screening rules therefore begin
 * warming immediately on a dedicated worker without blocking Application.onCreate(). If Telecom
 * arrives before warm-up completes, the screening callback may wait only for its own small bounded
 * budget and otherwise fails open. Heavier SMS/MMS repair remains outside process startup.
 */
class SentinelApplication : Application() {
    private val voiceTeardownScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val maintenanceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val maintenanceStarted = AtomicBoolean(false)

    fun launchVoiceTeardown(block: suspend () -> Unit) {
        voiceTeardownScope.launch {
            block()
        }
    }

    override fun onCreate() {
        super.onCreate()
        CallScreeningWarmup.start(applicationContext)
    }

    /**
     * Starts non-screening SMS/MMS repair only after a user-facing activity reaches RESUMED.
     *
     * The atomic gate prevents repeated onPostResume callbacks from creating duplicate work. A
     * failed scheduling attempt reopens the gate so a later resume can retry.
     */
    fun launchForegroundMaintenance() {
        if (!maintenanceStarted.compareAndSet(false, true)) return
        maintenanceScope.launch {
            if (!performMaintenance()) maintenanceStarted.set(false)
        }
    }

    /**
     * Performs bounded local repair and cache refresh after Telecom has received its response.
     *
     * CallScreeningService owns the only automatic caller. Keeping this synchronous is safe
     * because the caller is already the bounded post-response worker, never onScreenCall().
     */
    internal fun runPostResponseMaintenance() {
        if (!maintenanceStarted.compareAndSet(false, true)) return
        if (!performMaintenance()) maintenanceStarted.set(false)
    }

    private fun performMaintenance(): Boolean {
        var succeeded = true
        runCatching {
            SentinelSmsStatusReceiver.queueProviderRepair(applicationContext)
        }.onFailure {
            succeeded = false
        }
        runCatching {
            MmsSendCleanupWorker.scheduleStartupRecovery(applicationContext)
        }.onFailure {
            succeeded = false
        }
        runCatching {
            val store = CallBlocklistStore(applicationContext)
            val screeningSnapshot = store.prepareScreeningSnapshot()
            if (screeningSnapshot.blockedNumberHashes.isNotEmpty()) {
                store.prepareFingerprintKeys()
            }
        }.onFailure {
            succeeded = false
        }
        return succeeded
    }
}
