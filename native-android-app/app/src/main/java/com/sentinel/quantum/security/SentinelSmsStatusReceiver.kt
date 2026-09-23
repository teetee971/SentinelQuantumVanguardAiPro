package com.sentinel.quantum.security

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Records only delivery state; no destination or message body is logged. */
class SentinelSmsStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val stage = when (intent.action) {
            SentinelSmsSender.ACTION_SENT -> SmsDeliveryStatusBus.Stage.SENT
            SentinelSmsSender.ACTION_DELIVERED -> SmsDeliveryStatusBus.Stage.DELIVERED
            else -> return
        }
        val sendToken = intent.getIntExtra(SentinelSmsSender.EXTRA_SEND_TOKEN, -1)
        val partIndex = intent.getIntExtra(SentinelSmsSender.EXTRA_PART_INDEX, -1)
        val partCount = intent.getIntExtra(SentinelSmsSender.EXTRA_PART_COUNT, -1)
        val providerMessageId = intent.getLongExtra(SentinelSmsSender.EXTRA_PROVIDER_MESSAGE_ID, -1L)
        if (
            sendToken <= 0 ||
            providerMessageId <= 0L ||
            partCount !in 1..MAX_STATUS_PARTS ||
            partIndex !in 0 until partCount
        ) return

        val successful = resultCode == Activity.RESULT_OK
        val event = when (stage) {
            SmsDeliveryStatusBus.Stage.SENT ->
                if (successful) "SENT_OK" else "SENT_ERROR_" + resultCode
            SmsDeliveryStatusBus.Stage.DELIVERED ->
                if (successful) "DELIVERED_OK" else "DELIVERY_ERROR_" + resultCode
        }
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
            progress.failed -> conversationStore.markOutgoingFailed(providerMessageId)
            progress.allDelivered -> {
                conversationStore.markOutgoingSent(providerMessageId)
                conversationStore.markDeliveryResult(providerMessageId, true)
            }
            progress.allSent -> conversationStore.markOutgoingSent(providerMessageId)
        }
        SmsDeliveryStatusBus.publish(
            SmsDeliveryStatusBus.Event(
                sendToken = sendToken,
                partIndex = partIndex,
                partCount = partCount,
                stage = stage,
                successful = successful
            )
        )
        LocalLogger(context).log(LocalLogger.LogLevel.SECURITY, "DefaultSms", event)
        PhonePrivateTimelineStore(context).append(
            PhonePrivateTimeline.Event(
                kind = PhonePrivateTimeline.Kind.SMS,
                timestampMs = System.currentTimeMillis(),
                direction = "OUTGOING",
                signal = event
            )
        )
    }

    private companion object {
        const val MAX_STATUS_PARTS = 256
    }
}
