package com.sentinel.quantum

import android.app.Application
import com.sentinel.quantum.security.CallBlocklistStore
import com.sentinel.quantum.security.MmsSendCleanupWorker
import com.sentinel.quantum.security.SentinelSmsStatusReceiver
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Process-level initialization for call-screening state and bounded app maintenance.
 *
 * Existing fingerprint keys are loaded synchronously only when exact blocking rules exist. Android
 * creates the Application before CallScreeningService, so the service can remain strictly
 * cache-only without reintroducing the asynchronous cold-start race for exact-number rules.
 *
 * SMS/MMS provider repair is intentionally not started from [onCreate]. A Telecom-triggered cold
 * process start must not initialize unrelated WorkManager/provider maintenance before screening.
 * That maintenance is kicked only once the user-facing activity reaches RESUMED, on Dispatchers.IO.
 *
 * If AndroidKeyStore itself is unavailable, exact matching still fails open rather than risking a
 * false block; prefix and signed-prefix rules remain available.
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

    /**
     * Starts non-screening startup repair only from a resumed foreground activity.
     *
     * The atomic gate prevents repeated onPostResume() callbacks from creating duplicate startup
     * work. A synchronous scheduling failure reopens the gate so a later resume can retry.
     */
    fun launchForegroundMaintenance() {
        if (!maintenanceStarted.compareAndSet(false, true)) return
        maintenanceScope.launch {
            var schedulingSucceeded = true
            runCatching {
                SentinelSmsStatusReceiver.queueProviderRepair(applicationContext)
            }.onFailure {
                schedulingSucceeded = false
            }
            runCatching {
                MmsSendCleanupWorker.scheduleStartupRecovery(applicationContext)
            }.onFailure {
                schedulingSucceeded = false
            }
            if (!schedulingSucceeded) maintenanceStarted.set(false)
        }
    }

    override fun onCreate() {
        super.onCreate()

        val store = CallBlocklistStore(this)
        val screeningSnapshot = store.prepareScreeningSnapshot()
        if (screeningSnapshot.blockedNumberHashes.isNotEmpty()) {
            runCatching { store.prepareFingerprintKeys() }
        }
    }
}
