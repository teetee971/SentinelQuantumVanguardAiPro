package com.sentinel.quantum

import android.app.Application
import com.sentinel.quantum.security.CallBlocklistStore
import com.sentinel.quantum.security.MmsConversationStore
import com.sentinel.quantum.security.MmsSendPduStager
import com.sentinel.quantum.security.SentinelSmsStatusReceiver

/**
 * Process-level initialization for exact-number call blocking and durable telecom repair.
 *
 * Existing fingerprint keys are loaded synchronously only when exact blocking rules exist. Android
 * creates the Application before CallScreeningService, so the service can remain strictly
 * cache-only without the previous asynchronous cold-start race.
 *
 * MMS provider recovery and stale private PDU cleanup are also triggered here so process death does
 * not leave their repair dependent on a future send action.
 *
 * If AndroidKeyStore itself is unavailable, exact matching still fails open rather than risking a
 * false block; prefix and signed-prefix rules remain available.
 */
class SentinelApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SentinelSmsStatusReceiver.queueProviderRepair(this)
        runCatching { MmsSendPduStager.pruneExpired(this) }
        runCatching { MmsConversationStore(this).repairJournal() }

        val store = CallBlocklistStore(this)
        val screeningSnapshot = store.prepareScreeningSnapshot()
        if (screeningSnapshot.blockedNumberHashes.isNotEmpty()) {
            runCatching { store.prepareFingerprintKeys() }
        }
    }
}
