package com.sentinel.quantum

import android.app.Application
import com.sentinel.quantum.security.CallBlocklistStore
import com.sentinel.quantum.security.MmsSendCleanupWorker
import com.sentinel.quantum.security.SentinelSmsStatusReceiver

/**
 * Process-level initialization for exact-number call blocking and durable telecom repair.
 *
 * Existing fingerprint keys are loaded synchronously only when exact blocking rules exist. Android
 * creates the Application before CallScreeningService, so the service can remain strictly
 * cache-only without the previous asynchronous cold-start race.
 *
 * MMS provider recovery and stale private PDU cleanup are delegated to WorkManager so ContentResolver
 * and file I/O never run on the Application main thread.
 *
 * SMS authorization changes are observed read-only so a framework permission/AppOp transition
 * cannot leave the visible compose surface on a stale actionable snapshot.
 *
 * If AndroidKeyStore itself is unavailable, exact matching still fails open rather than risking a
 * false block; prefix and signed-prefix rules remain available.
 */
class SentinelApplication : Application() {
    private var smsActivationStateCoordinator: SmsActivationStateCoordinator? = null

    override fun onCreate() {
        super.onCreate()
        smsActivationStateCoordinator = SmsActivationStateCoordinator(this).also { it.start() }
        SentinelSmsStatusReceiver.queueProviderRepair(this)
        runCatching { MmsSendCleanupWorker.scheduleStartupRecovery(this) }

        val store = CallBlocklistStore(this)
        val screeningSnapshot = store.prepareScreeningSnapshot()
        if (screeningSnapshot.blockedNumberHashes.isNotEmpty()) {
            runCatching { store.prepareFingerprintKeys() }
        }
    }
}
