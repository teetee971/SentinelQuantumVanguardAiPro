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
import java.util.UUID

/**
 * Bounded outgoing MMS transport.
 *
 * "accepted" means only that Sentinel composed/staged the PDU and handed the request to Android.
 * Carrier success is reported later by [SentinelMmsSendStatusReceiver].
 */
class SentinelMmsSender(private val context: Context) {
    data class Attachment(val mimeType: String, val payload: ByteArray)

    data class SendResult(
        val accepted: Boolean,
        val reason: String,
        val subscriptionId: Int? = null,
        val token: String? = null
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
        if (
            context.readSmsRoleStateFailClosed() !=
                SmsActivationDiagnostics.SmsRoleState.HELD
        ) {
            return SendResult(false, "SMS_ROLE_NOT_HELD")
        }
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            return SendResult(false, "SEND_SMS_PERMISSION_NOT_GRANTED")
        }
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            return SendResult(false, "READ_PHONE_STATE_PERMISSION_NOT_GRANTED")
        }

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
                destination = destination,
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
        val transactionId = "sentinel-" + UUID.randomUUID().toString()
        val composed = SentinelMmsSendPduComposer.compose(
            destination = destination.trim(),
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

        val callbackIntent = Intent(context, SentinelMmsSendStatusReceiver::class.java)
            .setAction(ACTION_SENT)
            .setData(Uri.parse("sentinel-mms-send://result/${staged.token}"))
            .putExtra(EXTRA_FILE_NAME, staged.fileName)
            .putExtra(EXTRA_SUBSCRIPTION_ID, subscriptionId)
        val callback = PendingIntent.getBroadcast(
            context,
            staged.token.hashCode(),
            callbackIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return try {
            @Suppress("DEPRECATION")
            val manager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
                    .createForSubscriptionId(subscriptionId)
            } else {
                SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
            }
            manager.sendMultimediaMessage(
                context,
                staged.contentUri,
                null,
                null,
                callback
            )
            SendResult(
                accepted = true,
                reason = "MMS_SUBMITTED_TO_ANDROID",
                subscriptionId = subscriptionId,
                token = staged.token
            )
        } catch (_: Exception) {
            MmsSendPduStager.delete(context, staged.fileName)
            SendResult(false, "MMS_SUBMISSION_OUTCOME_UNKNOWN")
        }
    }

    companion object {
        const val ACTION_SENT = "com.sentinel.quantum.MMS_SENT_RESULT"
        const val EXTRA_FILE_NAME = "mms.send.file"
        const val EXTRA_SUBSCRIPTION_ID = "mms.send.subscription"
    }
}
