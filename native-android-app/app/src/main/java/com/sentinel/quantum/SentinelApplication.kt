package com.sentinel.quantum

import android.app.Application
import com.sentinel.quantum.security.CallBlocklistStore
import com.sentinel.quantum.security.IncomingSmsRecoveryWorker
import com.sentinel.quantum.security.IncomingMmsWapIngressRecoveryWorker
import com.sentinel.quantum.security.MmsSendCleanupWorker
import com.sentinel.quantum.security.MmsSubmissionWatchdogWorker
import com.sentinel.quantum.security.SentinelSmsStatusReceiver
import com.sentinel.quantum.security.SmsSubmissionWatchdogWorker

/**
 * Process-level initialization for exact-number call blocking and durable telecom repair.
 *
 * Existing fingerprint keys are loaded synchronously only when exact blocking rules exist. Android
 * creates the Application before CallScreeningService, so the service can remain strictly
 * cache-only without the previous asynchronous cold-start race.
 *
 * SMS ingress replay plus MMS provider recovery/cleanup are delegated to WorkManager so provider
 * projection and secondary file/content work never depend on a process-local unbounded queue.
 *
 * If AndroidKeyStore itself is unavailable, exact matching still fails open rather than risking a
 * false block; prefix and signed-prefix rules remain available.
 */
class SentinelApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SentinelSmsStatusReceiver.queueProviderRepair(this)
        runCatching { SmsSubmissionWatchdogWorker.schedule(this) }
        runCatching { IncomingSmsRecoveryWorker.schedule(this) }
        runCatching { IncomingMmsWapIngressRecoveryWorker.schedule(this) }
        runCatching { MmsSendCleanupWorker.scheduleStartupRecovery(this) }
        runCatching { MmsSubmissionWatchdogWorker.schedule(this) }

        val store = CallBlocklistStore(this)
        val screeningSnapshot = store.prepareScreeningSnapshot()
        if (screeningSnapshot.blockedNumberHashes.isNotEmpty()) {
            runCatching { store.prepareFingerprintKeys() }
        }
    }
}
