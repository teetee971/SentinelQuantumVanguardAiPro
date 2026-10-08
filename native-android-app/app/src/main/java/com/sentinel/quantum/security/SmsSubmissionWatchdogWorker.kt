package com.sentinel.quantum.security

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Resolves SMS submissions whose Android callbacks were lost across a crash, reboot or process
 * eviction. It marks only rows that are still OUTBOX/QUEUED; a terminal provider row is preserved.
 */
class SmsSubmissionWatchdogWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {
    override fun doWork(): Result {
        val appContext = applicationContext
        val ledger = SmsOutgoingSubmissionStore(appContext)
        val store = SmsConversationStore(appContext)
        val progressStore = SmsCallbackProgressStore(appContext)
        var retry = false
        val pendingProviderWrites = runCatching {
            progressStore.pendingProviderWrites()
        }.getOrElse {
            return Result.retry()
        }.associateBy { it.providerMessageId }

        ledger.stale().forEach { submission ->
            val pending = pendingProviderWrites[submission.providerMessageId]
            if (
                pending != null &&
                (
                    pending.outcome.sendFailed ||
                        pending.outcome.allSent ||
                        pending.outcome.deliveryFailed ||
                        pending.outcome.allDelivered
                    )
            ) {
                val providerApplied = runCatching {
                    val providerUpdated = SmsProviderPersistence.persist(
                        pending.outcome,
                        { store.markOutgoingFailed(submission.providerMessageId) },
                        { store.markOutgoingSent(submission.providerMessageId) },
                        { store.markDeliveryResult(submission.providerMessageId, it) }
                    )
                    providerUpdated && progressStore.markProviderApplied(
                        pending.sendToken,
                        submission.providerMessageId,
                        pending.outcome.state
                    )
                }.getOrDefault(false)
                if (!providerApplied) {
                    retry = true
                    return@forEach
                }
                ledger.remove(submission.sendToken, submission.providerMessageId)
                return@forEach
            }

            val resolved = runCatching {
                store.markOutgoingTimedOut(submission.providerMessageId)
            }.getOrDefault(false)
            if (!resolved) {
                retry = true
                return@forEach
            }

            ledger.remove(submission.sendToken, submission.providerMessageId)
            runCatching {
                PhonePrivateTimelineStore(appContext).append(
                    PhonePrivateTimeline.Event(
                        kind = PhonePrivateTimeline.Kind.SMS,
                        timestampMs = System.currentTimeMillis(),
                        direction = "OUTGOING",
                        signal = "SMS_CALLBACK_TIMEOUT"
                    )
                )
            }
            LocalLogger(appContext).log(
                LocalLogger.LogLevel.WARNING,
                "SmsStatus",
                "Statut SMS non reçu dans le délai; ligne provider résolue en échec"
            )
        }
        return if (retry) Result.retry() else Result.success()
    }

    companion object {
        private const val WORK_NAME = "sentinel-sms-submission-watchdog-v1"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SmsSubmissionWatchdogWorker>(
                SmsOutgoingSubmissionStore.CALLBACK_TIMEOUT_MS,
                TimeUnit.MILLISECONDS
            ).build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
