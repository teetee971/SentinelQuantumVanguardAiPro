package com.sentinel.quantum.security

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Resolves outgoing MMS provider rows abandoned when Android never delivered its callback. */
class MmsSubmissionWatchdogWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {
    override fun doWork(): Result {
        val appContext = applicationContext
        val journal = MmsProviderJournal(appContext)
        val store = MmsConversationStore(appContext)
        var retry = false

        journal.staleTransportSubmissions().forEach { record ->
            val providerMessageId = record.providerMessageId ?: return@forEach
            val resolved = runCatching {
                store.markTransportTimedOut(providerMessageId) && journal.remove(record.token)
            }.getOrDefault(false)
            if (!resolved) {
                retry = true
                return@forEach
            }

            runCatching {
                PhonePrivateTimelineStore(appContext).append(
                    PhonePrivateTimeline.Event(
                        kind = PhonePrivateTimeline.Kind.MMS,
                        timestampMs = System.currentTimeMillis(),
                        direction = "OUTGOING",
                        signal = "MMS_CALLBACK_TIMEOUT"
                    )
                )
            }
            LocalLogger(appContext).log(
                LocalLogger.LogLevel.WARNING,
                "MmsSend",
                "Callback MMS non reçu dans le délai; ligne provider résolue en échec"
            )
        }
        return if (retry) Result.retry() else Result.success()
    }

    companion object {
        private const val WORK_NAME = "sentinel-mms-submission-watchdog-v1"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MmsSubmissionWatchdogWorker>(
                MmsProviderJournal.MMS_CALLBACK_TIMEOUT_MS,
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
