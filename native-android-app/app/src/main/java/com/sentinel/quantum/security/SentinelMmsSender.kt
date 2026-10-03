package com.sentinel.quantum.security

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat

/**
 * Bounded outgoing MMS transport.
 *
 * "accepted" means only that Sentinel persisted/staged the message and handed the request to
 * Android. Carrier success is reported later by [SentinelMmsSendStatusReceiver].
 */
class SentinelMmsSender(private val context: Context) {
    data class Attachment(val mimeType: String, val payload: ByteArray)

    data class SendResult(
        val accepted: Boolean,
        val reason: String,
        val subscriptionId: Int? = null,
        val token: String? = null,
        val providerMessageId: Long? = null
    )

    fun send(
        destination: String,
        text: String,
        requestedSubscriptionId: Int? = null,
        attachments: List<Attachment> = emptyList()
    ): SendResult {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_MESSAGING)) {
            return SendResult(false, "TELEPHONY_MESSAGING_UNAVAILABLE")
        }
        if (context.readSmsRoleStateFailClosed() != SmsActivationDiagnostics.SmsRoleState.HELD) {
            return SendResult(false, "SMS_ROLE_NOT_HELD")
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            return SendResult(false, "SEND_SMS_PERMISSION_NOT_GRANTED")
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return SendResult(false, "READ_PHONE_STATE_PERMISSION_NOT_GRANTED")
        }

        val normalizedDestination = CallRuleEngine.normalizeNumber(destination)
            ?: return SendResult(false, "INVALID_DESTINATION")

        val activeIds = runCatching {
            context.getSystemService(SubscriptionManager::class.java)
                .activeSubscriptionInfoList
                .orEmpty()
                .map { it.subscriptionId }
                .filter { it != SubscriptionManager.INVALID_SUBSCRIPTION_ID }
                .toSet()
        }.getOrElse {
            return SendResult(false, "MMS_SUBSCRIPTION_LOOKUP_FAILED")
        }
        val defaultId = SubscriptionManager.getDefaultSmsSubscriptionId()
            .takeUnless { it == SubscriptionManager.INVALID_SUBSCRIPTION_ID }
        val selection = SmsSubscriptionSelectionPolicy.select(
            activeSubscriptionIds = activeIds,
            requestedSubscriptionId = requestedSubscriptionId,
            defaultSubscriptionId = defaultId
        )
        if (!selection.accepted || selection.subscriptionId == null) {
            return SendResult(false, selection.reason)
        }
        val subscriptionId = selection.subscriptionId

        val policyAttachments = attachments.map {
            MmsSendEligibilityPolicy.Attachment(it.mimeType, it.payload.size.toLong())
        }
        when (
            val eligibility = MmsSendEligibilityPolicy.evaluate(
                roleState = SmsActivationDiagnostics.SmsRoleState.HELD,
                subscriptionId = subscriptionId,
                destination = normalizedDestination,
                text = text,
                attachments = policyAttachments
            )
        ) {
            MmsSendEligibilityPolicy.Result.Eligible -> Unit
            is MmsSendEligibilityPolicy.Result.Rejected -> return SendResult(false, eligibility.reason)
        }

        val parts = attachments.map {
            SentinelMmsSendPduComposer.Part(it.mimeType.lowercase(), it.payload)
        }
        // The composer contract accepts at most 40 printable ASCII characters. A UUID is 36;
        // the old "sentinel-" prefix made every generated transaction id 45 chars and therefore
        // rejected the real sender path before SmsManager was reached.
        val transactionId = MmsTransactionIdFactory.create()
        val composed = SentinelMmsSendPduComposer.compose(
            destination = normalizedDestination,
            transactionId = transactionId,
            text = text,
            attachments = parts
        )
        val pdu = (composed as? SentinelMmsSendPduComposer.Result.Composed)?.pdu
            ?: return SendResult(
                false,
                (composed as SentinelMmsSendPduComposer.Result.Rejected).reason
            )

        val staged = MmsSendPduStager.stage(context, pdu)
        if (staged !is MmsSendPduStager.Result.Staged) {
            return SendResult(false, (staged as MmsSendPduStager.Result.Rejected).reason)
        }

        val providerStore = MmsConversationStore(context)
        runCatching { providerStore.repairJournal() }.onFailure {
            LocalLogger(context).log(
                LocalLogger.LogLevel.WARNING,
                "MmsProvider",
                "Réparation provider MMS différée; le nouvel envoi reste soumis à sa propre transaction"
            )
        }
        val providerAttachments = attachments.map {
            MmsConversationStore.Attachment(it.mimeType.lowercase(), it.payload)
        }
        val providerResult = providerStore.persistOutgoingOutbox(
            token = staged.token,
            transactionId = transactionId,
            destination = normalizedDestination,
            text = text,
            attachments = providerAttachments,
            subscriptionId = subscriptionId
        )
        val providerMessageId = when (providerResult) {
            is MmsConversationStore.PersistResult.Ready -> providerResult.providerMessageId
            is MmsConversationStore.PersistResult.Rejected -> {
                MmsSendPduStager.delete(context, staged.fileName)
                if (!providerResult.cleanupConfirmed) {
                    LocalLogger(context).log(
                        LocalLogger.LogLevel.WARNING,
                        "MmsProvider",
                        "Projection MMS incomplète; journal conservé pour récupération"
                    )
                }
                return SendResult(false, providerResult.reason, subscriptionId, staged.token)
            }
        }

        var transportInvocationStarted = false
        return try {
            val callbackIntent = Intent(context, SentinelMmsSendStatusReceiver::class.java)
                .setAction(ACTION_SENT)
                .setData(Uri.parse("sentinel-mms-send://result/${staged.token}"))
                .putExtra(EXTRA_FILE_NAME, staged.fileName)
                .putExtra(EXTRA_SUBSCRIPTION_ID, subscriptionId)
                .putExtra(EXTRA_PROVIDER_MESSAGE_ID, providerMessageId)
            val callback = PendingIntent.getBroadcast(
                context,
                staged.token.hashCode(),
                callbackIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            @Suppress("DEPRECATION")
            val manager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
                    .createForSubscriptionId(subscriptionId)
            } else {
                SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
            }

            // From this instruction onward, a synchronous exception cannot prove that the platform
            // accepted no MMS bytes. Only callbacks may resolve that uncertainty.
            transportInvocationStarted = true
            manager.sendMultimediaMessage(context, staged.contentUri, null, null, callback)
            if (!providerStore.markSubmitted(staged.token, providerMessageId)) {
                LocalLogger(context).log(
                    LocalLogger.LogLevel.WARNING,
                    "MmsProvider",
                    "Transport MMS soumis mais transition journal SUBMITTED non confirmée"
                )
            }
            SendResult(
                accepted = true,
                reason = "MMS_SUBMITTED_TO_ANDROID",
                subscriptionId = subscriptionId,
                token = staged.token,
                providerMessageId = providerMessageId
            )
        } catch (_: Exception) {
            MmsSendPduStager.delete(context, staged.fileName)
            if (transportInvocationStarted) {
                // The Android telephony call was entered. Keep OUTBOX and record uncertainty;
                // deleting or failing the row here could contradict a late carrier callback.
                providerStore.markSubmissionUnknown(staged.token, providerMessageId)
                SendResult(
                    accepted = false,
                    reason = "MMS_SUBMISSION_OUTCOME_UNKNOWN",
                    subscriptionId = subscriptionId,
                    token = staged.token,
                    providerMessageId = providerMessageId
                )
            } else {
                // No transport invocation happened. Compensate the provider row instead of leaving
                // a fake pending message. If cleanup cannot be proven the recovery journal remains.
                val cleanupConfirmed = providerStore.abandonBeforeTransport(staged.token, providerMessageId)
                if (!cleanupConfirmed) {
                    LocalLogger(context).log(
                        LocalLogger.LogLevel.WARNING,
                        "MmsProvider",
                        "Préparation transport MMS échouée; nettoyage provider à reprendre"
                    )
                }
                SendResult(
                    accepted = false,
                    reason = "MMS_SUBMISSION_PREPARATION_FAILED",
                    subscriptionId = subscriptionId,
                    token = staged.token,
                    providerMessageId = providerMessageId
                )
            }
        }
    }

    companion object {
        const val ACTION_SENT = "com.sentinel.quantum.MMS_SENT_RESULT"
        const val EXTRA_FILE_NAME = "mms.send.file"
        const val EXTRA_SUBSCRIPTION_ID = "mms.send.subscription"
        const val EXTRA_PROVIDER_MESSAGE_ID = "mms.send.provider_message_id"
    }
}
