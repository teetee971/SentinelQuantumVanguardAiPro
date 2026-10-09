package com.sentinel.quantum.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Handles the explicit result callback for an outgoing MMS request.
 *
 * Callback identity/result are captured synchronously; cache cleanup, replay rejection, provider
 * projection, timeline writes and notifications are serialized off the BroadcastReceiver main
 * thread. Android transport success is never described as recipient delivery.
 */
class SentinelMmsSendStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != SentinelMmsSender.ACTION_SENT) return
        val callbackUri = intent.data ?: return
        if (callbackUri.scheme != "sentinel-mms-send" || callbackUri.host != "result") return

        val token = callbackUri.pathSegments.singleOrNull()
            ?.takeIf { TOKEN.matches(it) }
            ?: return
        val fileName = intent.getStringExtra(SentinelMmsSender.EXTRA_FILE_NAME)
            ?.takeIf { it == "$token.pdu" && FILE_NAME.matches(it) }
            ?: return
        val subscriptionId = intent.getIntExtra(
            SentinelMmsSender.EXTRA_SUBSCRIPTION_ID,
            SubscriptionManager.INVALID_SUBSCRIPTION_ID
        )
        if (!MmsSubscriptionResolver.isValidSubscriptionId(subscriptionId)) return

        // Keep callbacks created by an older installed build processable. They do not carry the
        // provider correlation id introduced with the provider projection. Such a callback may
        // still update transport truth/timeline, but it cannot claim a provider projection write.
        val providerMessageId = intent.getLongExtra(
            SentinelMmsSender.EXTRA_PROVIDER_MESSAGE_ID,
            -1L
        ).takeIf { it > 0L }

        val androidResultCode = resultCode
        val httpStatus = intent.getIntExtra(SmsManager.EXTRA_MMS_HTTP_STATUS, Int.MIN_VALUE)
            .takeUnless { it == Int.MIN_VALUE }
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val scheduled = runCatching {
            WORKER.execute {
                try {
                    process(
                        appContext,
                        token,
                        fileName,
                        subscriptionId,
                        providerMessageId,
                        androidResultCode,
                        httpStatus
                    )
                } finally {
                    pendingResult.finish()
                }
            }
        }.isSuccess
        if (!scheduled) pendingResult.finish()
    }

    private fun process(
        context: Context,
        token: String,
        fileName: String,
        subscriptionId: Int,
        providerMessageId: Long?,
        androidResultCode: Int,
        httpStatus: Int?
    ) {
        val acceptedOnce = MmsSendCallbackReplayGuard(context).acceptOnce(token)
        if (!acceptedOnce) {
            MmsSendPduStager.delete(context, fileName)
            LocalLogger(context).log(
                LocalLogger.LogLevel.WARNING,
                "MmsSend",
                "Callback MMS dupliqué ou tombstone non persistable; aucune nouvelle transition créée"
            )
            return
        }

        MmsSendPduStager.delete(context, fileName)
        val outcome = MmsSendResultClassifier.classify(androidResultCode, httpStatus)

        var providerUpdated = false
        if (providerMessageId != null) {
            val providerStore = MmsConversationStore(context)
            providerUpdated = providerStore.applyTransportResult(
                token = token,
                providerMessageId = providerMessageId,
                successful = outcome.success
            )
            if (!providerUpdated) {
                LocalLogger(context).log(
                    LocalLogger.LogLevel.WARNING,
                    "MmsProvider",
                    "Callback MMS reçu mais projection provider non confirmée; réparation journalisée"
                )
                queueProviderRepair(context)
            }
        } else {
            LocalLogger(context).log(
                LocalLogger.LogLevel.WARNING,
                "MmsProvider",
                "Callback MMS antérieur à la corrélation provider; transport traité sans revendiquer de mise à jour provider"
            )
        }

        runCatching {
            PhonePrivateTimelineStore(context).append(
                PhonePrivateTimeline.Event(
                    kind = PhonePrivateTimeline.Kind.MMS,
                    timestampMs = System.currentTimeMillis(),
                    direction = "OUTGOING",
                    signal = outcome.signal
                )
            )
        }.onFailure {
            LocalLogger(context).log(
                LocalLogger.LogLevel.WARNING,
                "MmsSend",
                "Chronologie privée indisponible; le callback MMS reste traité"
            )
        }

        val providerState = when {
            providerMessageId == null -> "legacy_uncorrelated"
            providerUpdated -> "applied"
            else -> "pending_repair"
        }
        LocalLogger(context).log(
            if (outcome.success) LocalLogger.LogLevel.SECURITY else LocalLogger.LogLevel.WARNING,
            "MmsSend",
            "Callback transport MMS; state=${outcome.diagnostic}; code=$androidResultCode; " +
                "http=${httpStatus ?: "none"}; subscription=$subscriptionId; provider=$providerState"
        )
        SmsNotificationHelper.notifyMessage(
            context,
            title = outcome.title,
            preview = outcome.preview,
            notificationId = fileName.hashCode()
        )
    }

    private fun queueProviderRepair(context: Context) {
        if (!repairQueued.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        val scheduled = runCatching {
            REPAIR_WORKER.schedule(
                {
                    // Clear before the repair so a callback arriving during the repair can
                    // enqueue one bounded follow-up instead of being lost behind this pass.
                    repairQueued.set(false)
                    runCatching { MmsConversationStore(appContext).repairJournal() }
                },
                PROVIDER_REPAIR_DELAY_SECONDS,
                TimeUnit.SECONDS
            )
        }.isSuccess
        if (!scheduled) repairQueued.set(false)
    }

    private companion object {
        const val PROVIDER_REPAIR_DELAY_SECONDS = 60L
        const val MAX_PENDING_CALLBACKS = 32
        val repairQueued = AtomicBoolean(false)
        val WORKER = ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue<Runnable>(MAX_PENDING_CALLBACKS),
            { task -> Thread(task, "sentinel-mms-send").apply { isDaemon = true } },
            ThreadPoolExecutor.AbortPolicy()
        )
        val REPAIR_WORKER = Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, "sentinel-mms-provider-repair").apply { isDaemon = true }
        }
        val TOKEN = Regex(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
        )
        val FILE_NAME = Regex(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.pdu$"
        )
    }
}
