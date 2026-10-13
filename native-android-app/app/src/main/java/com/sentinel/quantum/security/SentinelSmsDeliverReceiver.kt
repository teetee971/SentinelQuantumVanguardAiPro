package com.sentinel.quantum.security

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SubscriptionManager
import java.util.concurrent.Executors

/**
 * Receives SMS_DELIVER only when Android routes the default-SMS broadcast to Sentinel.
 * Raw message content is written to the Android SMS provider; logs never contain the body.
 */
class SentinelSmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        if (!holdsSmsRole(context)) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val deliveredIntent = Intent(intent)
        val submitted = runCatching {
            WORKER.execute {
                try {
                    processDelivery(appContext, deliveredIntent)
                } catch (_: Exception) {
                    LocalLogger(appContext).log(
                        LocalLogger.LogLevel.WARNING,
                        "DefaultSms",
                        "Échec inattendu du traitement d'un SMS entrant"
                    )
                } finally {
                    pendingResult.finish()
                }
            }
        }.isSuccess
        if (!submitted) {
            LocalLogger(appContext).log(
                LocalLogger.LogLevel.WARNING,
                "DefaultSms",
                "SMS entrant non planifié : worker indisponible"
            )
            pendingResult.finish()
        }
    }

    private fun processDelivery(context: Context, intent: Intent) {
        // The default handler may change while this broadcast waits for the serial worker.
        // Re-read Android at the asynchronous processing boundary instead of retaining onReceive's truth.
        if (!holdsSmsRole(context)) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty() || messages.size > MAX_SMS_PARTS) {
            LocalLogger(context).log(
                LocalLogger.LogLevel.WARNING,
                "DefaultSms",
                "SMS entrant rejeté : nombre de parties invalide"
            )
            return
        }

        val address = messages.firstNotNullOfOrNull { it.originatingAddress }
            .orEmpty()
            .trim()
            .take(MAX_ADDRESS_CHARS)
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
        val identity = SmsIngressIdentity.from(
            address = address,
            body = body,
            sentAtMillis = sentAt ?: 0L,
            subscriptionId = subscriptionId
        )
        if (identity != null && hasExistingIncomingSms(context, identity)) {
            LocalLogger(context).log(
                LocalLogger.LogLevel.INFO,
                "DefaultSms",
                "Replay SMS entrant ignoré : identité provider déjà présente"
            )
            return
        }
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, receivedAt)
            sentAt?.let { put(Telephony.Sms.DATE_SENT, it) }
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
            subscriptionId?.let { put(Telephony.Sms.SUBSCRIPTION_ID, it) }
        }

        val inserted = runCatching {
            context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
        }.getOrNull()

        val logger = LocalLogger(context)
        if (inserted != null) {
            runCatching {
                PhonePrivateTimelineStore(context).append(
                    PhonePrivateTimeline.Event(
                        kind = PhonePrivateTimeline.Kind.SMS,
                        timestampMs = receivedAt,
                        direction = "INCOMING",
                        signal = PhoneCorePhysicalValidation.SIGNAL_SMS_RECEIVED
                    )
                )
            }

            val smsAnalysis = runCatching { SmsLinkAnalyzer(logger).analyze(body) }.getOrNull()
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
                    title = address,
                    preview = body,
                    notificationId = (receivedAt xor address.hashCode().toLong()).toInt()
                )
            }.getOrDefault(false)
            if (notificationPosted) {
                runCatching {
                    PhonePrivateTimelineStore(context).append(
                        PhonePrivateTimeline.Event(
                            kind = PhonePrivateTimeline.Kind.SMS,
                            timestampMs = receivedAt,
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
        } else {
            logger.log(
                LocalLogger.LogLevel.WARNING,
                "DefaultSms",
                "Échec d'enregistrement d'un SMS entrant"
            )
        }
    }

    private fun hasExistingIncomingSms(context: Context, identity: SmsIngressIdentity.Key): Boolean {
        val selection = buildString {
            append(Telephony.Sms.ADDRESS).append("=?")
            append(" AND ").append(Telephony.Sms.BODY).append("=?")
            append(" AND ").append(Telephony.Sms.DATE_SENT).append("=?")
            append(" AND ").append(Telephony.Sms.TYPE).append("=?")
            identity.subscriptionId?.let {
                append(" AND ").append(Telephony.Sms.SUBSCRIPTION_ID).append("=?")
            }
        }
        val selectionArgs = buildList {
            add(identity.address)
            add(identity.body)
            add(identity.sentAtMillis.toString())
            add(Telephony.Sms.MESSAGE_TYPE_INBOX.toString())
            identity.subscriptionId?.let { add(it.toString()) }
        }.toTypedArray()
        return try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms._ID),
                selection,
                selectionArgs,
                null
            )?.use { cursor -> cursor.moveToFirst() } ?: true
        } catch (_: Exception) {
            // Do not insert when the provider cannot answer the replay check: otherwise a
            // transient provider failure can turn one delivery into two durable inbox rows.
            LocalLogger(context).log(
                LocalLogger.LogLevel.WARNING,
                "DefaultSms",
                "Vérification de replay SMS indisponible; insertion bloquée"
            )
            true
        }
    }

    private fun holdsSmsRole(context: Context): Boolean =
        context.readSmsRoleStateFailClosed() == SmsActivationDiagnostics.SmsRoleState.HELD

    private companion object {
        const val EXTRA_SUBSCRIPTION_INDEX = "android.telephony.extra.SUBSCRIPTION_INDEX"
        const val EXTRA_LEGACY_SUBSCRIPTION = "subscription"
        val WORKER = Executors.newSingleThreadExecutor { task ->
            Thread(task, "sentinel-sms-deliver").apply { isDaemon = true }
        }
        const val MAX_SMS_PARTS = 32
        const val MAX_ADDRESS_CHARS = 128
    }
}
