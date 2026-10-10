package com.sentinel.quantum.security

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Records only delivery state; no destination or message body is logged.
 *
 * Android telephony callbacks are validated synchronously, then processed on one private serial
 * executor via goAsync(). Serial processing preserves multipart provider transitions while keeping
 * ContentResolver, preferences and timeline I/O off BroadcastReceiver's main thread.
 */
class SentinelSmsStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val callbackUri = intent.data ?: return
        if (
            callbackUri.scheme != CALLBACK_URI_SCHEME ||
            callbackUri.host != CALLBACK_URI_HOST
        ) return

        val path = callbackUri.pathSegments
        if (path.size != 5) return
        val uriSendToken = path[0].toIntOrNull()?.takeIf { it > 0 } ?: return
        val uriProviderMessageId = path[1].toLongOrNull()?.takeIf { it > 0L } ?: return
        val uriPartIndex = path[2].toIntOrNull() ?: return
        val uriPartCount = path[3].toIntOrNull() ?: return
        val callbackKind = path[4]

        val sendToken = intent.getIntExtra(SentinelSmsSender.EXTRA_SEND_TOKEN, -1)
        val providerMessageId =
            intent.getLongExtra(SentinelSmsSender.EXTRA_PROVIDER_MESSAGE_ID, -1L)
        val partIndex = intent.getIntExtra(SentinelSmsSender.EXTRA_PART_INDEX, -1)
        val partCount = intent.getIntExtra(SentinelSmsSender.EXTRA_PART_COUNT, -1)

        if (
            sendToken != uriSendToken ||
            providerMessageId != uriProviderMessageId ||
            partIndex != uriPartIndex ||
            partCount != uriPartCount ||
            partCount !in 1..SmsCallbackProgress.MAX_PARTS ||
            partIndex !in 0 until partCount
        ) return

        val stage = when {
            intent.action == SentinelSmsSender.ACTION_SENT && callbackKind == "sent" ->
                SmsDeliveryStatusBus.Stage.SENT
            intent.action == SentinelSmsSender.ACTION_DELIVERED && callbackKind == "delivered" ->
                SmsDeliveryStatusBus.Stage.DELIVERED
            else -> return
        }

        val androidResultCode = resultCode
        val successful = androidResultCode == Activity.RESULT_OK
        val appContext = context.applicationContext
        val pendingResult = goAsync()

        try {
            CALLBACK_EXECUTOR.execute {
                try {
                    processValidatedCallback(
                        context = appContext,
                        stage = stage,
                        sendToken = sendToken,
                        providerMessageId = providerMessageId,
                        partIndex = partIndex,
                        partCount = partCount,
                        successful = successful,
                        androidResultCode = androidResultCode
                    )
                } catch (_: Exception) {
                    queueProviderRepair(appContext)
                    LocalLogger(appContext).log(
                        LocalLogger.LogLevel.WARNING,
                        "SmsStatus",
                        "Échec inattendu du callback SMS; réparation provider planifiée"
                    )
                } finally {
                    pendingResult.finish()
                }
            }
        } catch (_: RuntimeException) {
            val captured = runCatching {
                captureAndScheduleAfterSaturation(
                    context = appContext,
                    stage = stage,
                    sendToken = sendToken,
                    providerMessageId = providerMessageId,
                    partIndex = partIndex,
                    partCount = partCount,
                    successful = successful
                )
            }.getOrDefault(false)
            if (!captured) {
                LocalLogger(appContext).logAsync(
                    LocalLogger.LogLevel.WARNING,
                    "SmsStatus",
                    "Callback SMS non persisté après saturation de la file"
                )
            }
            pendingResult.finish()
        }
    }

    /**
     * Queue-full fallback. Only the bounded opaque progress record is committed here; provider
     * projection and timeline work remain on the repair scheduler.
     */
    private fun captureAndScheduleAfterSaturation(
        context: Context,
        stage: SmsDeliveryStatusBus.Stage,
        sendToken: Int,
        providerMessageId: Long,
        partIndex: Int,
        partCount: Int,
        successful: Boolean
    ): Boolean {
        var persistenceFailed = false
        val outcome = SmsCallbackProgressStore(context).record(
            sendToken = sendToken,
            providerMessageId = providerMessageId,
            partIndex = partIndex,
            partCount = partCount,
            stage = stage,
            successful = successful,
            onPersistenceFailure = { persistenceFailed = true }
        ) ?: return false
        if (persistenceFailed) {
            LocalLogger(context).logAsync(
                LocalLogger.LogLevel.WARNING,
                "SmsStatus",
                "Progression SMS capturée mais persistance non confirmée; réparation planifiée"
            )
        }
        queueProviderRepair(context)
        return true
    }

    private fun processValidatedCallback(
        context: Context,
        stage: SmsDeliveryStatusBus.Stage,
        sendToken: Int,
        providerMessageId: Long,
        partIndex: Int,
        partCount: Int,
        successful: Boolean,
        androidResultCode: Int
    ) {
        val event = when (stage) {
            SmsDeliveryStatusBus.Stage.SENT ->
                if (successful) "SENT_OK" else "SENT_ERROR_" + androidResultCode
            SmsDeliveryStatusBus.Stage.DELIVERED ->
                if (successful) "DELIVERED_OK" else "DELIVERY_ERROR_" + androidResultCode
        }

        val progressStore = SmsCallbackProgressStore(context)
        var progressPersistenceFailed = false
        val progress = runCatching {
            progressStore.record(
                sendToken = sendToken,
                providerMessageId = providerMessageId,
                partIndex = partIndex,
                partCount = partCount,
                stage = stage,
                successful = successful,
                onPersistenceFailure = { progressPersistenceFailed = true }
            )
        }.getOrElse {
            progressPersistenceFailed = true
            null
        }
        if (progress == null) {
            if (progressPersistenceFailed) {
                SmsDeliveryStatusBus.publish(SmsDeliveryStatusBus.Event(
                    sendToken, providerMessageId, partIndex, partCount, stage, successful,
                    providerWriteSucceeded = false
                ))
                LocalLogger(context).log(LocalLogger.LogLevel.WARNING, "SmsStatus", "Persistance du statut SMS indisponible")
            }
            return
        }

        if (progressPersistenceFailed) {
            LocalLogger(context).log(LocalLogger.LogLevel.WARNING, "SmsStatus", "Persistance du statut SMS indisponible")
        }
        val conversationStore = SmsConversationStore(context)
        val providerUpdated = SmsProviderPersistence.persist(
            progress = progress,
            markFailed = { conversationStore.markOutgoingFailed(providerMessageId) },
            markSent = { conversationStore.markOutgoingSent(providerMessageId) },
            markDelivery = { conversationStore.markDeliveryResult(providerMessageId, it) }
        )
        val providerApplied = providerUpdated && runCatching {
            progressStore.markProviderApplied(sendToken, providerMessageId, progress.state)
        }.getOrDefault(false)
        // The outgoing-submission ledger protects the radio submission boundary only. Once every
        // SENT callback has a verdict, delivery reports may continue in the progress store without
        // occupying admission capacity or blocking a later user-initiated message.
        if (providerApplied && progress.submissionResolved) {
            SmsOutgoingSubmissionStore(context).remove(sendToken, providerMessageId)
        }
        if (!providerUpdated) {
            queueProviderRepair(context)
            LocalLogger(context).log(LocalLogger.LogLevel.WARNING, "SmsStatus", "Écriture du statut dans le provider SMS non confirmée")
        }

        SmsDeliveryStatusBus.publish(
            SmsDeliveryStatusBus.Event(
                sendToken = sendToken,
                providerMessageId = providerMessageId,
                partIndex = partIndex,
                partCount = partCount,
                stage = stage,
                successful = successful,
                providerWriteSucceeded = providerUpdated && !progressPersistenceFailed
            )
        )
        LocalLogger(context).log(LocalLogger.LogLevel.SECURITY, "DefaultSms", event)
        val timeline = PhonePrivateTimelineStore(context)
        val timestampMs = System.currentTimeMillis()
        runCatching {
            check(timeline.append(
                PhonePrivateTimeline.Event(
                    kind = PhonePrivateTimeline.Kind.SMS,
                    timestampMs = timestampMs,
                    direction = "OUTGOING",
                    signal = event
                )
            )) { "Timeline write not confirmed" }
        }.onFailure {
            LocalLogger(context).log(
                LocalLogger.LogLevel.WARNING,
                "SmsStatus",
                "Chronologie privée indisponible; le traitement du statut SMS continue"
            )
        }

        if (providerUpdated && !progressPersistenceFailed) progress.certificationSignals.forEach { signal ->
            runCatching {
                check(timeline.append(
                    PhonePrivateTimeline.Event(
                        kind = PhonePrivateTimeline.Kind.SMS,
                        timestampMs = timestampMs,
                        direction = "OUTGOING",
                        signal = signal
                    )
                )) { "Timeline write not confirmed" }
            }.onFailure {
                LocalLogger(context).log(
                    LocalLogger.LogLevel.WARNING,
                    "SmsStatus",
                    "Chronologie privée indisponible; le traitement du statut SMS continue"
                )
            }
        }
    }

    companion object {
        private val repairQueued = AtomicBoolean(false)
        private const val MAX_PENDING_CALLBACKS = 64

        fun queueProviderRepair(context: Context) {
            if (!repairQueued.compareAndSet(false, true)) return
            val appContext = context.applicationContext
            REPAIR_SCHEDULER.schedule({
                repairQueued.set(false)
                val store = SmsCallbackProgressStore(appContext)
                var retry = false
                val pending = runCatching { store.pendingProviderWrites() }.getOrElse {
                    retry = true
                    emptyList()
                }
                val conversations = SmsConversationStore(appContext)
                for (write in pending) {
                    val id = write.providerMessageId
                    val applied = SmsProviderPersistence.persist(write.outcome,
                        { conversations.markOutgoingFailed(id) },
                        { conversations.markOutgoingSent(id) },
                        { conversations.markDeliveryResult(id, it) })
                    if (!applied || !runCatching {
                            store.markProviderApplied(write.sendToken, id, write.outcome.state)
                        }.getOrDefault(false)) retry = true
                    else if (write.outcome.submissionResolved) {
                        SmsOutgoingSubmissionStore(appContext).remove(write.sendToken, id)
                    }
                }
                // Repair is a provider projection only; it must never manufacture physical proofs.
                if (retry) queueProviderRepair(appContext)
            }, 60L, TimeUnit.SECONDS)
        }

        const val CALLBACK_URI_SCHEME = "sentinel-sms-status"
        const val CALLBACK_URI_HOST = "callback"
        private val CALLBACK_EXECUTOR = ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue<Runnable>(MAX_PENDING_CALLBACKS),
            { runnable -> Thread(runnable, "SentinelSmsStatus").apply { isDaemon = true } },
            ThreadPoolExecutor.AbortPolicy()
        )
        private val REPAIR_SCHEDULER = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "SentinelSmsStatusRepair").apply { isDaemon = true }
        }
    }
}
