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
import androidx.core.content.PermissionChecker

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
        if (
            !context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_MESSAGING)
        ) {
            return SendResult(false, "TELEPHONY_MESSAGING_UNAVAILABLE")
        }
        checkOutboundAuthorization()?.let { return SendResult(false, it) }

        val normalizedDestination = CallRuleEngine.normalizeNumber(destination)
            ?: return SendResult(false, "INVALID_DESTINATION")

        val activeIds = when (val state = SmsSubscriptionState(context).load()) {
            SmsSubscriptionState.Result.PermissionRequired ->
                return SendResult(false, "READ_PHONE_STATE_PERMISSION_NOT_GRANTED")
            SmsSubscriptionState.Result.LookupFailed ->
                return SendResult(false, "MMS_SUBSCRIPTION_LOOKUP_FAILED")
            is SmsSubscriptionState.Result.Available -> state.subscriptions.map { it.subscriptionId }.toSet()
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
            is MmsSendEligibilityPolicy.Result.Rejected ->
                return SendResult(false, eligibility.reason)
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

            checkOutboundAuthorization(subscriptionId)?.let { reason ->
                // Known refusal before Android saw the staged PDU. Keep the user's provider row.
                runCatching { providerStore.recordRejectedBeforeTransport(staged.token, providerMessageId) }
                return SendResult(false, reason, subscriptionId, staged.token, providerMessageId)
            }

            // From this instruction onward, a synchronous exception cannot prove that the platform
            // accepted no MMS bytes. Only callbacks may resolve that uncertainty.
            transportInvocationStarted = true
            manager.sendMultimediaMessage(
                context,
                staged.contentUri,
                null,
                null,
                callback
            )
            // Android returned normally. A later journal or diagnostic failure cannot change
            // that submission into a refusal or trigger compensation of accepted content.
            runCatching {
                if (!providerStore.markSubmitted(staged.token, providerMessageId)) {
                    LocalLogger(context).log(
                        LocalLogger.LogLevel.WARNING,
                        "MmsProvider",
                        "Transport MMS soumis mais transition journal SUBMITTED non confirmée"
                    )
                }
            }
            SendResult(
                accepted = true,
                reason = "MMS_SUBMITTED_TO_ANDROID",
                subscriptionId = subscriptionId,
                token = staged.token,
                providerMessageId = providerMessageId
            )
        } catch (_: Exception) {
            if (transportInvocationStarted) {
                // The Android telephony call was entered. Keep both OUTBOX and staged PDU because
                // a synchronous exception does not prove Android stopped consuming the content URI.
                // The callback or the bounded cache-prune path owns PDU cleanup from this point.
                runCatching {
                    if (!providerStore.markSubmissionUnknown(staged.token, providerMessageId)) {
                        LocalLogger(context).log(
                            LocalLogger.LogLevel.WARNING,
                            "MmsProvider",
                            "Issue de soumission MMS inconnue et journal SUBMISSION_UNKNOWN non confirmé"
                        )
                    }
                }
                SendResult(
                    accepted = false,
                    reason = "MMS_SUBMISSION_OUTCOME_UNKNOWN",
                    subscriptionId = subscriptionId,
                    token = staged.token,
                    providerMessageId = providerMessageId
                )
            } else {
                MmsSendPduStager.delete(context, staged.fileName)
                // No transport invocation happened. Compensate the provider row instead of leaving
                // a fake pending message. If cleanup cannot be proven the recovery journal remains.
                val cleanupConfirmed = providerStore.abandonBeforeTransport(
                    staged.token,
                    providerMessageId
                )
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

    private fun checkOutboundAuthorization(subscriptionId: Int? = null): String? {
        outboundPermissionDenial()?.let { return it }
        if (subscriptionId == null) return null
        val active = SmsSubscriptionState(context).load()
        outboundPermissionDenial()?.let { return it }
        return when (active) {
            SmsSubscriptionState.Result.PermissionRequired -> "READ_PHONE_STATE_PERMISSION_NOT_GRANTED"
            SmsSubscriptionState.Result.LookupFailed -> "MMS_SUBSCRIPTION_LOOKUP_FAILED"
            is SmsSubscriptionState.Result.Available -> if (active.subscriptions.none { it.subscriptionId == subscriptionId }) {
                "REQUESTED_SUBSCRIPTION_NOT_ACTIVE"
            } else null
        }
    }

    private fun outboundPermissionDenial(): String? {
        if (context.readSmsRoleStateFailClosed() != SmsActivationDiagnostics.SmsRoleState.HELD) {
            return "SMS_ROLE_NOT_HELD"
        }
        if (!hasEffectivePermission(Manifest.permission.SEND_SMS)) return "SEND_SMS_PERMISSION_NOT_GRANTED"
        if (!hasEffectivePermission(Manifest.permission.READ_PHONE_STATE)) {
            return "READ_PHONE_STATE_PERMISSION_NOT_GRANTED"
        }
        return null
    }

    private fun hasEffectivePermission(permission: String): Boolean = runCatching {
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED &&
            PermissionChecker.checkSelfPermission(context, permission) == PermissionChecker.PERMISSION_GRANTED
    }.getOrDefault(false)

    companion object {
        const val ACTION_SENT = "com.sentinel.quantum.MMS_SENT_RESULT"
        const val EXTRA_FILE_NAME = "mms.send.file"
        const val EXTRA_SUBSCRIPTION_ID = "mms.send.subscription"
        const val EXTRA_PROVIDER_MESSAGE_ID = "mms.send.provider_message_id"
    }
}
