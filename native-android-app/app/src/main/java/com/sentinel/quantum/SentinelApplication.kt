package com.sentinel.quantum

import android.app.Application
import com.sentinel.quantum.security.CallBlocklistStore
import com.sentinel.quantum.security.MmsSendCleanupWorker
import com.sentinel.quantum.security.PhoneRegionRuntimeCache
import com.sentinel.quantum.security.SentinelSmsStatusReceiver

/**
 * Process-level initialization for exact-number call blocking and durable telecom repair.
 *
 * Existing fingerprint keys are loaded synchronously only when exact blocking rules exist. Android
 * creates the Application before CallScreeningService, so the service can remain strictly
 * cache-only without the previous asynchronous cold-start race.
 *
 * Phone-region observation is warmed outside the screening callback. Until an unambiguous active
 * subscription region is available, national-number matching deliberately fails open; explicit
 * international numbers remain usable without a region lookup.
 *
 * MMS provider recovery and stale private PDU cleanup are delegated to WorkManager so ContentResolver
 * and file I/O never run on the Application main thread.
 *
 * If AndroidKeyStore itself is unavailable, exact matching still fails open rather than risking a
 * false block; prefix and signed-prefix rules remain available.
 */
class SentinelApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PhoneRegionRuntimeCache.start(this)
        SentinelSmsStatusReceiver.queueProviderRepair(this)
        runCatching { MmsSendCleanupWorker.scheduleStartupRecovery(this) }

        val store = CallBlocklistStore(this)
        val screeningSnapshot = store.prepareScreeningSnapshot()
        if (screeningSnapshot.blockedNumberHashes.isNotEmpty()) {
            runCatching { store.prepareFingerprintKeys() }
        }
    }
}
