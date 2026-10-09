package com.sentinel.quantum

import android.app.Application
import com.sentinel.quantum.ptt.PttProcessRuntime
import com.sentinel.quantum.security.CallBlocklistStore
import com.sentinel.quantum.security.IncomingSmsRecoveryWorker
import com.sentinel.quantum.security.IncomingMmsWapIngressRecoveryWorker
import com.sentinel.quantum.security.MmsSendCleanupWorker
import com.sentinel.quantum.security.MmsSubmissionWatchdogWorker
import com.sentinel.quantum.security.LocalLogger
import com.sentinel.quantum.security.SentinelInCallService
import com.sentinel.quantum.security.SentinelSmsStatusReceiver
import com.sentinel.quantum.security.SmsSubmissionWatchdogWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

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
    private val processScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        observeTelecomPriorityForPtt()

        scheduleRecovery("réparation provider SMS") {
            SentinelSmsStatusReceiver.queueProviderRepair(this@SentinelApplication)
        }
        scheduleRecovery("watchdog soumission SMS") {
            SmsSubmissionWatchdogWorker.schedule(this@SentinelApplication)
        }
        scheduleRecovery("récupération SMS entrant") {
            IncomingSmsRecoveryWorker.schedule(this@SentinelApplication)
        }
        scheduleRecovery("récupération WAP Push MMS") {
            IncomingMmsWapIngressRecoveryWorker.schedule(this@SentinelApplication)
        }
        scheduleRecovery("nettoyage MMS sortant") {
            MmsSendCleanupWorker.scheduleStartupRecovery(this@SentinelApplication)
        }
        scheduleRecovery("watchdog soumission MMS") {
            MmsSubmissionWatchdogWorker.schedule(this@SentinelApplication)
        }

        scheduleRecovery("préchargement des règles de filtrage") {
            val store = CallBlocklistStore(this@SentinelApplication)
            val screeningSnapshot = store.prepareScreeningSnapshot()
            if (screeningSnapshot.blockedNumberHashes.isNotEmpty()) {
                store.prepareFingerprintKeys()
            }
        }
    }

    private fun observeTelecomPriorityForPtt() {
        runCatching {
            SentinelInCallService.sessions
                .map { session -> session.calls.isNotEmpty() }
                .distinctUntilChanged()
                .onEach { present -> PttProcessRuntime.onTelecomCallPresenceChanged(present) }
                .launchIn(processScope)
        }.onFailure {
            runCatching {
                LocalLogger(this).logAsync(
                    LocalLogger.LogLevel.WARNING,
                    "PTT",
                    "Priorité des appels Telecom indisponible; aucun runtime PTT ne doit être déclaré prêt"
                )
            }
        }
    }

    private fun scheduleRecovery(label: String, action: () -> Unit) {
        runCatching { action() }.onFailure {
            runCatching {
                LocalLogger(this).logAsync(
                    LocalLogger.LogLevel.WARNING,
                    "Recovery",
                    "Planification de $label impossible; la prochaine initialisation retentera la récupération"
                )
            }
        }
    }
}
