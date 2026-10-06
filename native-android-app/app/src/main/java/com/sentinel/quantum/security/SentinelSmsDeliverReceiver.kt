package com.sentinel.quantum.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SubscriptionManager

/**
 * Receives SMS_DELIVER only when Android routes the default-SMS broadcast to Sentinel.
 *
 * The broadcast no longer waits behind an unbounded in-memory executor. The minimum message record
 * is fsync'd to app-private storage first, then a unique WorkManager job projects it to Android's SMS
 * provider and performs secondary analysis/notification. A process death therefore leaves replayable
 * state instead of an orphaned PendingResult. Raw message content is never written to logs.
 */
class SentinelSmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        if (!holdsSmsRole(context)) return

        val appContext = context.applicationContext
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty() || messages.size > MAX_SMS_PARTS) {
            LocalLogger(appContext).log(
                LocalLogger.LogLevel.WARNING,
                "DefaultSms",
                "SMS entrant rejeté : nombre de parties invalide"
            )
            return
        }

        val address = messages.firstNotNullOfOrNull { it.originatingAddress }
            .orEmpty()
            .trim()
            .take(IncomingSmsDeliveryStore.MAX_ADDRESS_CHARS)
        val body = buildString {
            messages.forEach { message ->
                val remaining = SentinelSmsSender.MAX_BODY_CHARS - length
                if (remaining <= 0) return@forEach
                append(message.messageBody.orEmpty().take(remaining))
            }
        }
        if (address.isBlank() || body.isBlank()) return

        val receivedAt = System.currentTimeMillis()
        val sentAt = messages
            .map { it.timestampMillis }
            .filter { it > 0L }
            .minOrNull()
        val subscriptionId = sequenceOf(
            intent.getIntExtra(EXTRA_SUBSCRIPTION_INDEX, SubscriptionManager.INVALID_SUBSCRIPTION_ID),
            intent.getIntExtra(EXTRA_LEGACY_SUBSCRIPTION, SubscriptionManager.INVALID_SUBSCRIPTION_ID)
        ).firstOrNull { it != SubscriptionManager.INVALID_SUBSCRIPTION_ID && it >= 0 }
        val pdus = rawPdus(intent)
        val id = IncomingSmsDeliveryStore.identity(
            pdus = pdus,
            address = address,
            body = body,
            sentAtMs = sentAt,
            subscriptionId = subscriptionId
        ) ?: return
        val record = IncomingSmsDeliveryStore.Record(
            id = id,
            address = address,
            body = body,
            receivedAtMs = receivedAt,
            sentAtMs = sentAt,
            subscriptionId = subscriptionId
        )

        val persistState = IncomingSmsDeliveryStore.persist(appContext.filesDir, record)
        when (persistState) {
            IncomingSmsDeliveryStore.PersistState.CREATED,
            IncomingSmsDeliveryStore.PersistState.EXISTING -> {
                val scheduled = runCatching {
                    IncomingSmsDeliveryWorker.schedule(appContext, id)
                    true
                }.getOrDefault(false)
                if (!scheduled) {
                    // For an EXISTING replay, retain the original durable receive timestamp. Using
                    // this broadcast's new wall-clock timestamp would defeat exact provider replay
                    // lookup and could duplicate a message after a previous insert/cleanup crash.
                    val projectionRecord = if (persistState == IncomingSmsDeliveryStore.PersistState.EXISTING) {
                        IncomingSmsDeliveryStore.read(appContext.filesDir, id) ?: record
                    } else {
                        record
                    }
                    val projected = IncomingSmsDeliveryWorker.projectImmediately(
                        context = appContext,
                        record = projectionRecord,
                        deleteStageOnSuccess = true
                    )
                    if (!projected) logCaptureFailure(appContext)
                }
            }
            IncomingSmsDeliveryStore.PersistState.CAPACITY_EXCEEDED,
            IncomingSmsDeliveryStore.PersistState.FAILED -> {
                // Do not reject a user message merely because the durable spool is unavailable.
                // Apply bounded backpressure at the system provider instead of accumulating memory.
                val projected = IncomingSmsDeliveryWorker.projectImmediately(
                    context = appContext,
                    record = record,
                    deleteStageOnSuccess = false
                )
                if (!projected) logCaptureFailure(appContext)
            }
        }
    }

    private fun rawPdus(intent: Intent): List<ByteArray> {
        @Suppress("DEPRECATION")
        val raw = intent.extras?.get("pdus") as? Array<*> ?: return emptyList()
        return raw.mapNotNull { it as? ByteArray }.take(MAX_SMS_PARTS)
    }

    private fun logCaptureFailure(context: Context) {
        LocalLogger(context).log(
            LocalLogger.LogLevel.WARNING,
            "DefaultSms",
            "SMS entrant non projeté immédiatement; reprise durable indisponible"
        )
    }

    private fun holdsSmsRole(context: Context): Boolean =
        context.readSmsRoleStateFailClosed() == SmsActivationDiagnostics.SmsRoleState.HELD

    private companion object {
        const val EXTRA_SUBSCRIPTION_INDEX = "android.telephony.extra.SUBSCRIPTION_INDEX"
        const val EXTRA_LEGACY_SUBSCRIPTION = "subscription"
        const val MAX_SMS_PARTS = 32
    }
}
