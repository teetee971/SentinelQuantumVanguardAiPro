package com.sentinel.quantum.security

import android.content.ContentValues
import android.content.Context
import android.provider.Telephony
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/** Projects durably staged SMS_DELIVER records into the canonical Android provider. */
class IncomingSmsDeliveryWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {
    override fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.success()
        val record = IncomingSmsDeliveryStore.read(applicationContext.filesDir, id)
        if (record == null) {
            val retained = IncomingSmsDeliveryStore.hasPending(applicationContext.filesDir, id)
            if (retained) {
                LocalLogger(applicationContext).log(
                    LocalLogger.LogLevel.WARNING,
                    "DefaultSms",
                    "Spool SMS entrant illisible encore présent; nouvelle tentative durable planifiée"
                )
            }
            return if (retained) Result.retry() else Result.success()
        }

        val projection = runCatching {
            project(applicationContext, record, deleteStageOnSuccess = true)
        }.getOrElse {
            LocalLogger(applicationContext).log(
                LocalLogger.LogLevel.WARNING,
                "DefaultSms",
                "Échec inattendu de la projection SMS; nouvelle tentative durable planifiée"
            )
            Projection.RETRY
        }

        return when (projection) {
            Projection.SUCCESS -> Result.success()
            Projection.RETRY -> Result.retry()
            Projection.ROLE_UNAVAILABLE -> {
                val age = (System.currentTimeMillis() - record.receivedAtMs).coerceAtLeast(0L)
                if (age < ROLE_RETRY_WINDOW_MS) {
                    Result.retry()
                } else {
                    val retired = IncomingSmsDeliveryStore.delete(applicationContext.filesDir, record.id)
                    LocalLogger(applicationContext).log(
                        LocalLogger.LogLevel.WARNING,
                        "DefaultSms",
                        if (retired) {
                            "Spool SMS entrant expiré après perte prolongée du rôle SMS; contenu privé retiré"
                        } else {
                            "Spool SMS entrant expiré mais suppression privée impossible; nouvelle tentative planifiée"
                        }
                    )
                    if (retired) Result.success() else Result.retry()
                }
            }
        }
    }

    internal enum class Projection {
        SUCCESS,
        RETRY,
        ROLE_UNAVAILABLE
    }

    companion object {
        fun schedule(context: Context, id: String) {
            val request = OneTimeWorkRequestBuilder<IncomingSmsDeliveryWorker>()
                .setInputData(workDataOf(KEY_ID to id))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.SECONDS)
                .addTag(WORK_TAG)
                .addTag(FILE_TAG_PREFIX + id)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                WORK_PREFIX + id,
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        fun schedulePendingNow(context: Context) {
            IncomingSmsDeliveryStore.pendingIds(context.filesDir).forEach { id ->
                runCatching { schedule(context.applicationContext, id) }
            }
        }

        /**
         * Synchronous no-loss fallback used only when durable staging or WorkManager submission is
         * unavailable. It performs the minimal provider projection and then best-effort enrichment.
         */
        internal fun projectImmediately(
            context: Context,
            record: IncomingSmsDeliveryStore.Record,
            deleteStageOnSuccess: Boolean
        ): Boolean =
            project(context.applicationContext, record, deleteStageOnSuccess) == Projection.SUCCESS

        private fun project(
            context: Context,
            record: IncomingSmsDeliveryStore.Record,
            deleteStageOnSuccess: Boolean
        ): Projection {
            if (
                context.readSmsRoleStateFailClosed() !=
                    SmsActivationDiagnostics.SmsRoleState.HELD
            ) {
                return Projection.ROLE_UNAVAILABLE
            }

            when (providerLookup(context, record)) {
                ProviderLookup.FOUND -> {
                    if (
                        deleteStageOnSuccess &&
                        !IncomingSmsDeliveryStore.delete(context.filesDir, record.id)
                    ) {
                        return Projection.RETRY
                    }
                    return Projection.SUCCESS
                }
                ProviderLookup.UNKNOWN -> return Projection.RETRY
                ProviderLookup.NOT_FOUND -> Unit
            }

            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, record.address)
                put(Telephony.Sms.BODY, record.body)
                put(Telephony.Sms.DATE, record.receivedAtMs)
                record.sentAtMs?.let { put(Telephony.Sms.DATE_SENT, it) }
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
                put(Telephony.Sms.READ, 0)
                put(Telephony.Sms.SEEN, 0)
                record.subscriptionId?.let { put(Telephony.Sms.SUBSCRIPTION_ID, it) }
            }

            val inserted = runCatching {
                context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
            }.getOrNull() ?: return Projection.RETRY

            if (deleteStageOnSuccess) {
                // Provider durability is the primary product invariant. If private-stage cleanup
                // fails, a later replay uses the exact provider identity and does not insert twice.
                if (!IncomingSmsDeliveryStore.delete(context.filesDir, record.id)) {
                    return Projection.RETRY
                }
            }
            enrichBestEffort(context, record)
            return Projection.SUCCESS
        }

        private fun providerLookup(
            context: Context,
            record: IncomingSmsDeliveryStore.Record
        ): ProviderLookup {
            val selection = buildString {
                append(Telephony.Sms.ADDRESS).append(" = ? AND ")
                append(Telephony.Sms.BODY).append(" = ? AND ")
                append(Telephony.Sms.DATE).append(" = ? AND ")
                append(Telephony.Sms.TYPE).append(" = ?")
                if (record.subscriptionId != null) {
                    append(" AND ").append(Telephony.Sms.SUBSCRIPTION_ID).append(" = ?")
                }
            }
            val args = buildList {
                add(record.address)
                add(record.body)
                add(record.receivedAtMs.toString())
                add(Telephony.Sms.MESSAGE_TYPE_INBOX.toString())
                record.subscriptionId?.let { add(it.toString()) }
            }.toTypedArray()

            return runCatching {
                context.contentResolver.query(
                    Telephony.Sms.Inbox.CONTENT_URI,
                    arrayOf(Telephony.Sms._ID),
                    selection,
                    args,
                    Telephony.Sms._ID + " DESC"
                )?.use { cursor ->
                    if (cursor.moveToFirst()) ProviderLookup.FOUND else ProviderLookup.NOT_FOUND
                } ?: ProviderLookup.UNKNOWN
            }.getOrDefault(ProviderLookup.UNKNOWN)
        }

        private fun enrichBestEffort(context: Context, record: IncomingSmsDeliveryStore.Record) {
            runCatching {
                PhonePrivateTimelineStore(context).append(
                    PhonePrivateTimeline.Event(
                        kind = PhonePrivateTimeline.Kind.SMS,
                        timestampMs = record.receivedAtMs,
                        direction = "INCOMING",
                        signal = PhoneCorePhysicalValidation.SIGNAL_SMS_RECEIVED
                    )
                )
            }

            val logger = LocalLogger(context)
            val smsAnalysis = runCatching { SmsLinkAnalyzer(logger).analyze(record.body) }.getOrNull()
            smsAnalysis?.let { analysis ->
                runCatching {
                    SmsTimelineMapper.toEvent(analysis)?.let { event ->
                        PhonePrivateTimelineStore(context).append(event)
                    }
                }
            }

            val notificationPosted = runCatching {
                SmsNotificationHelper.notifyMessage(
                    context,
                    title = record.address,
                    preview = record.body,
                    notificationId = (record.receivedAtMs xor record.address.hashCode().toLong()).toInt()
                )
            }.getOrDefault(false)
            if (notificationPosted) {
                runCatching {
                    PhonePrivateTimelineStore(context).append(
                        PhonePrivateTimeline.Event(
                            kind = PhonePrivateTimeline.Kind.SMS,
                            timestampMs = record.receivedAtMs,
                            direction = "INCOMING",
                            signal = PhoneCorePhysicalValidation.SIGNAL_SMS_NOTIFICATION_POSTED
                        )
                    )
                }
            }

            val analysisSummary = smsAnalysis?.let {
                "analyse locale=" + it.riskLevel.name + "; liens=" + it.linksInspected
            } ?: "analyse locale indisponible"
            logger.log(
                LocalLogger.LogLevel.SECURITY,
                "DefaultSms",
                "SMS entrant enregistré; " + analysisSummary
            )
        }

        private enum class ProviderLookup {
            FOUND,
            NOT_FOUND,
            UNKNOWN
        }

        internal const val ROLE_RETRY_WINDOW_MS = 7L * 24L * 60L * 60L * 1000L
        internal const val WORK_TAG = "sentinel-sms-inbound-delivery"
        private const val WORK_PREFIX = "sentinel-sms-inbound-v1-"
        private const val FILE_TAG_PREFIX = "sentinel-sms-inbound-file-"
        private const val KEY_ID = "sms.inbound.id"
    }
}
