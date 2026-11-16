package com.sentinel.quantum

import android.app.Application
import com.sentinel.quantum.background.PublishedNumberCatalogScheduler
import com.sentinel.quantum.data.SettingsStore
import com.sentinel.quantum.security.CallBlocklistStore
import com.sentinel.quantum.security.CallDirectoryCache
import com.sentinel.quantum.security.MmsSendCleanupWorker
import com.sentinel.quantum.security.PublishedNumberCatalogStore
import com.sentinel.quantum.security.SentinelSmsStatusReceiver
import com.sentinel.quantum.security.WearOsDataLayerAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Process-level services and lifecycle-safe maintenance for Sentinel.
 *
 * [onCreate] intentionally performs no screening preparation and starts no network refresh.
 * Android creates the Application before CallScreeningService, so disk, WorkManager,
 * AndroidKeyStore and wearable platform work here would extend the cold-start window before the
 * mandatory Telecom response. Maintenance and Wear OS observation start only after a user-facing
 * activity resumes or from the bounded post-response worker. Until one of those paths completes,
 * screening caches may fail open while the process is cold and wearable truth remains unobserved.
 * Unknown-caller silence stays fail-open until the contact directory has been loaded into memory.
 * The directory warm-up does not schedule MMS or SMS repair; that repair stays in
 * [performMaintenance]. Official catalog refresh starts once, from the resumed activity, never
 * from [onCreate] or from the screening callback.
 */
class SentinelApplication : Application() {
    private val voiceTeardownScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val maintenanceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val maintenanceStarted = AtomicBoolean(false)
    private val wearableObservationStarted = AtomicBoolean(false)
    private val catalogSyncStarted = AtomicBoolean(false)

    fun launchVoiceTeardown(block: suspend () -> Unit) {
        voiceTeardownScope.launch {
            block()
        }
    }

    override fun onCreate() {
        super.onCreate()
    }

    /**
     * Starts non-screening SMS/MMS repair only after a user-facing activity reaches RESUMED.
     *
     * The atomic gate prevents repeated onPostResume callbacks from creating duplicate work. A
     * failed scheduling attempt reopens the gate so a later resume can retry.
     */
    fun launchForegroundMaintenance() {
        launchWearableObservation()
        if (catalogSyncStarted.compareAndSet(false, true)) {
            maintenanceScope.launch {
                val started = runCatching {
                    PublishedNumberCatalogStore.bind(applicationContext)
                    val enabled = SettingsStore(applicationContext).isPublishedCatalogRefreshEnabled()
                    PublishedNumberCatalogScheduler.sync(applicationContext, enabled)
                }.isSuccess
                if (!started) catalogSyncStarted.set(false)
            }
        }
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

    private fun launchWearableObservation() {
        if (!wearableObservationStarted.compareAndSet(false, true)) return
        runCatching {
            WearOsDataLayerAdapter(applicationContext).observeConnectedNodes {
                // Runtime truth is published by the adapter/coordinator. A failed observation may
                // be retried after a later foreground resume without blocking the UI.
                if (it.failureReason != null) wearableObservationStarted.set(false)
            }
        }.onFailure {
            wearableObservationStarted.set(false)
        }
    }

    private fun warmScreeningCaches(): Boolean {
        val store = CallBlocklistStore(applicationContext)
        val snapshot = store.prepareScreeningSnapshot()
        if (snapshot.blockedNumberHashes.isNotEmpty()) {
            store.prepareFingerprintKeys()
        }
        return CallDirectoryCache.refresh(
            applicationContext,
            store.silenceOutsideDirectory() || store.neighborNoticeEnabled()
        )
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
