package com.sentinel.quantum.security

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.concurrent.Executors

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
                } finally {
                    pendingResult.finish()
                }
            }
        } catch (_: RuntimeException) {
            pendingResult.finish()
        }
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

        // A null outcome includes late callbacks against a retained terminal tombstone.
        val progress = SmsCallbackProgressStore(context).record(
            sendToken = sendToken,
            providerMessageId = providerMessageId,
            partIndex = partIndex,
            partCount = partCount,
            stage = stage,
            successful = successful
        ) ?: return

        val conversationStore = SmsConversationStore(context)
        when {
            progress.sendFailed -> conversationStore.markOutgoingFailed(providerMessageId)
            else -> {
                if (progress.allSent) {
                    conversationStore.markOutgoingSent(providerMessageId)
                }
                when {
                    progress.deliveryFailed ->
                        conversationStore.markDeliveryResult(providerMessageId, false)
                    progress.allDelivered ->
                        conversationStore.markDeliveryResult(providerMessageId, true)
                }
            }
        }

        SmsDeliveryStatusBus.publish(
            SmsDeliveryStatusBus.Event(
                sendToken = sendToken,
                providerMessageId = providerMessageId,
                partIndex = partIndex,
                partCount = partCount,
                stage = stage,
                successful = successful
            )
        )
        LocalLogger(context).log(LocalLogger.LogLevel.SECURITY, "DefaultSms", event)
        val timeline = PhonePrivateTimelineStore(context)
        val timestampMs = System.currentTimeMillis()
        runCatching {
            timeline.append(
                PhonePrivateTimeline.Event(
                    kind = PhonePrivateTimeline.Kind.SMS,
                    timestampMs = timestampMs,
                    direction = "OUTGOING",
                    signal = event
                )
            )
        }.onFailure {
            LocalLogger(context).log(
                LocalLogger.LogLevel.WARNING,
                "SmsStatus",
                "Chronologie privée indisponible; le traitement du statut SMS continue"
            )
        }

        val aggregateSignal = when {
            stage == SmsDeliveryStatusBus.Stage.DELIVERED && progress.allDelivered ->
                PhoneCorePhysicalValidation.SIGNAL_SMS_ALL_PARTS_DELIVERED
            stage == SmsDeliveryStatusBus.Stage.SENT && progress.allSent ->
                PhoneCorePhysicalValidation.SIGNAL_SMS_ALL_PARTS_SENT
            else -> null
        }
        aggregateSignal?.let { signal ->
            runCatching {
                timeline.append(
                    PhonePrivateTimeline.Event(
                        kind = PhonePrivateTimeline.Kind.SMS,
                        timestampMs = timestampMs,
                        direction = "OUTGOING",
                        signal = signal
                    )
                )
            }.onFailure {
                LocalLogger(context).log(
                    LocalLogger.LogLevel.WARNING,
                    "SmsStatus",
                    "Chronologie privée indisponible; le traitement du statut SMS continue"
                )
            }
        }
    }

    private companion object {
        const val CALLBACK_URI_SCHEME = "sentinel-sms-status"
        const val CALLBACK_URI_HOST = "callback"
        val CALLBACK_EXECUTOR = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "SentinelSmsStatus").apply { isDaemon = true }
        }
    }
}
