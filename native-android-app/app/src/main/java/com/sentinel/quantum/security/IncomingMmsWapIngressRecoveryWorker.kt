package com.sentinel.quantum.security

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Replays WAP PDUs durably captured when the MMS delivery executor was saturated. */
class IncomingMmsWapIngressRecoveryWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {
    override fun doWork(): Result {
        val journal = IncomingMmsWapIngressJournal(applicationContext)
        var retry = false
        val nowMs = System.currentTimeMillis()
        journal.all().forEach { record ->
            if (nowMs >= record.receivedAtMs &&
                nowMs - record.receivedAtMs >= MAX_RETRY_AGE_MS
            ) {
                val stagedRemoved = IncomingMmsWapIngressStore.delete(
                    applicationContext.filesDir,
                    record.digestHex
                )
                val journalRemoved = stagedRemoved && journal.remove(record.digestHex)
                if (!journalRemoved) retry = true
                return@forEach
            }
            val data = IncomingMmsWapIngressStore.read(applicationContext.filesDir, record.digestHex)
            if (data == null) {
                // The private bytes are no longer recoverable; do not retain metadata that can
                // never produce a message. The store's digest check prevents a substitute PDU.
                val stagedRemoved = IncomingMmsWapIngressStore.delete(
                    applicationContext.filesDir,
                    record.digestHex
                )
                val journalRemoved = stagedRemoved && journal.remove(record.digestHex)
                if (!journalRemoved) retry = true
                return@forEach
            }

            val outcome = runCatching {
                SentinelMmsDeliverReceiver().processRecovery(
                    context = applicationContext,
                    data = data,
                    record = record
                )
            }.getOrDefault(SentinelMmsDeliverReceiver.DeliveryOutcome.RETRY)
            if (outcome == SentinelMmsDeliverReceiver.DeliveryOutcome.RETRY) {
                retry = true
            } else {
                val stagedRemoved = IncomingMmsWapIngressStore.delete(
                    applicationContext.filesDir,
                    record.digestHex
                )
                val journalRemoved = stagedRemoved && journal.remove(record.digestHex)
                if (!journalRemoved) retry = true
            }
        }
        return if (retry) Result.retry() else Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<IncomingMmsWapIngressRecoveryWorker>()
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.SECONDS)
                .addTag(WORK_TAG)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_WORK,
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        internal const val WORK_TAG = "sentinel-mms-wap-ingress-recovery"
        internal const val MAX_RETRY_AGE_MS = 24L * 60L * 60L * 1000L
        private const val UNIQUE_WORK = "sentinel-mms-wap-ingress-recovery-v1"
    }
}
