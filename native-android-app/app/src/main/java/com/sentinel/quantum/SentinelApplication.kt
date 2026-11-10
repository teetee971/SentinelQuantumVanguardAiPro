package com.sentinel.quantum

import android.app.Application
import com.sentinel.quantum.background.PublishedNumberCatalogScheduler
import com.sentinel.quantum.data.SettingsStore
import com.sentinel.quantum.security.CallBlocklistStore
import com.sentinel.quantum.security.CallDirectoryCache
import com.sentinel.quantum.security.MmsSendCleanupWorker
import com.sentinel.quantum.security.PublishedNumberCatalogStore
import com.sentinel.quantum.security.SentinelSmsStatusReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Process-level services and lifecycle-safe maintenance for Sentinel.
 *
 * [onCreate] does not block on disk, WorkManager or the contact provider. It only schedules an
 * asynchronous warm-up of the in-memory screening snapshot and contact directory, then the
 * published numbering-catalog refresh. That work does not schedule MMS repair. Until the
 * screening warm-up publishes, unknown-caller silence stays fail-open.
 * MMS and SMS repair still start from a resumed activity or from the post-response worker.
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
        maintenanceScope.launch {
            runCatching { warmScreeningCaches() }
            runCatching {
                PublishedNumberCatalogStore.bind(applicationContext)
                val enabled = SettingsStore(applicationContext).isPublishedCatalogRefreshEnabled()
                PublishedNumberCatalogScheduler.sync(applicationContext, enabled)
            }
        }
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
     * Performs bounded local repair and cache warm-up after Telecom has received its response.
     *
     * CallScreeningService owns the only automatic caller. Keeping this synchronous is safe
     * because the caller is already the bounded post-response worker, never onScreenCall().
     */
    internal fun runPostResponseMaintenance() {
        if (!maintenanceStarted.compareAndSet(false, true)) return
        if (!performMaintenance()) maintenanceStarted.set(false)
    }

    private fun warmScreeningCaches(): Boolean {
        val store = CallBlocklistStore(applicationContext)
        val snapshot = store.prepareScreeningSnapshot()
        if (snapshot.blockedNumberHashes.isNotEmpty()) {
            store.prepareFingerprintKeys()
        }
        return CallDirectoryCache.refresh(applicationContext, store.silenceOutsideDirectory())
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
            if (!warmScreeningCaches()) succeeded = false
        }.onFailure {
            succeeded = false
        }
        return succeeded
    }
}
