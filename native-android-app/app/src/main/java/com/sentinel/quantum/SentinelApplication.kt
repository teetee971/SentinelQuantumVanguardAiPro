package com.sentinel.quantum

import android.app.Application
import com.sentinel.quantum.security.CallBlocklistStore
import com.sentinel.quantum.security.SentinelSmsStatusReceiver

/**
 * Process-level initialization for exact-number call blocking.
 *
 * Existing fingerprint keys are loaded synchronously only when exact blocking rules exist. Android
 * creates the Application before CallScreeningService, so the service can remain strictly
 * cache-only without the previous asynchronous cold-start race.
 *
 * If AndroidKeyStore itself is unavailable, exact matching still fails open rather than risking a
 * false block; prefix and signed-prefix rules remain available.
 */
class SentinelApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SentinelSmsStatusReceiver.queueProviderRepair(this)
        val store = CallBlocklistStore(this)
        val screeningSnapshot = store.prepareScreeningSnapshot()
        if (screeningSnapshot.blockedNumberHashes.isNotEmpty()) {
            runCatching { store.prepareFingerprintKeys() }
        }
    }
}
