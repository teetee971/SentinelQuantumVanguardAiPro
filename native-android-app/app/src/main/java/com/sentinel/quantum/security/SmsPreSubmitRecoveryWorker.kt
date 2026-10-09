package com.sentinel.quantum.security

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Repairs only SMS provider state that is durably proven to be pre-transport. */
class SmsPreSubmitRecoveryWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {
    override fun doWork(): Result {
        val journal = SmsPreSubmitJournal(applicationContext)
        val records = journal.all()
        if (records.isEmpty()) return Result.success()
        if (
            applicationContext.readSmsRoleStateFailClosed() !=
                SmsActivationDiagnostics.SmsRoleState.HELD
        ) {
            return Result.retry()
        }

        val store = SmsConversationStore(applicationContext)
        var retry = false
        records.forEach { record ->
            when (record.phase) {
                SmsPreSubmitJournal.Phase.PREPARING -> {
                    when (
                        store.repairPreparedOutbox(
                            timestampMs = record.createdAtMs,
                            subscriptionId = record.subscriptionId
                        )
                    ) {
                        SmsConversationStore.PreparedOutboxRepair.ABSENT,
                        SmsConversationStore.PreparedOutboxRepair.REPAIRED -> {
                            if (!journal.remove(record.token)) retry = true
                        }
                        SmsConversationStore.PreparedOutboxRepair.AMBIGUOUS,
                        SmsConversationStore.PreparedOutboxRepair.FAILED -> retry = true
                    }
                }
                SmsPreSubmitJournal.Phase.PROVIDER_READY -> {
                    val providerId = record.providerMessageId
                    if (providerId == null || !store.markOutgoingFailed(providerId)) {
                        retry = true
                    } else if (!journal.remove(record.token)) {
                        retry = true
                    }
                }
                SmsPreSubmitJournal.Phase.TRANSPORT_STARTED -> {
                    // Never infer a transport failure from an ambiguous process-death boundary.
                }
            }
        }
        return if (retry) Result.retry() else Result.success()
    }

    companion object {
        fun scheduleStartupRecovery(context: Context) {
            val request = OneTimeWorkRequestBuilder<SmsPreSubmitRecoveryWorker>()
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.SECONDS)
                .addTag(WORK_TAG)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        internal const val WORK_TAG = "sentinel-sms-pre-submit-recovery"
        private const val WORK_NAME = "sentinel-sms-pre-submit-recovery-v1"
    }
}
