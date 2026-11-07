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
class SentinelMmsSender internal constructor(private val context: Context, private val effects: Effects = AndroidEffects(context)) {
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
            !runCatching { effects.hasMessagingFeature() }.getOrDefault(false)
        ) {
            return SendResult(false, "TELEPHONY_MESSAGING_UNAVAILABLE")
        }
        authorizationFailure()?.let { return SendResult(false, it) }

        val normalizedDestination = CallRuleEngine.normalizeNumber(destination)
            ?: return SendResult(false, "INVALID_DESTINATION")

        val activeIds = runCatching { effects.activeSubscriptionIds() }.getOrElse {
            return SendResult(false, "MMS_SUBSCRIPTION_LOOKUP_FAILED")
        }
        val defaultId = runCatching { effects.defaultSubscriptionId() }.getOrElse {
            return SendResult(false, "MMS_SUBSCRIPTION_LOOKUP_FAILED")
        }
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

        // Query the same subscription-specific SmsManager used for the actual transport.
        // Do not guess from another SIM or a global default when its configuration is unavailable.
        val carrier = runCatching { effects.carrier(subscriptionId) }
            .getOrElse { return SendResult(false, "MMS_CARRIER_CONFIG_UNAVAILABLE", subscriptionId) }
        val maxPduBytes = runCatching { carrier.maxPduBytes }.getOrDefault(0)
        if (maxPduBytes <= 0) {
            return SendResult(false, "MMS_CARRIER_CONFIG_UNAVAILABLE", subscriptionId)
        }

        val parts = attachments.map {
            SentinelMmsSendPduComposer.Part(it.mimeType.lowercase(), it.payload)
        }
        // The composer contract accepts at most 40 printable ASCII characters. A UUID is 36;
        // the old "sentinel-" prefix made every generated transaction id 45 chars and therefore
        // rejected the real sender path before SmsManager was reached.
        val transactionId = MmsTransactionIdFactory.create()
        val prepared = try {
            effects.prepare(normalizedDestination, transactionId, text, parts, maxPduBytes)
        } catch (_: OutOfMemoryError) {
            return SendResult(false, "MMS_JPEG_MEMORY_LIMIT", subscriptionId)
        } catch (_: Exception) {
            return SendResult(false, "MMS_IMAGE_PREPARATION_FAILED", subscriptionId)
        }
        if (prepared is MmsOutgoingPreparation.Result.Rejected) {
            return SendResult(false, prepared.reason, subscriptionId)
        }
        prepared as MmsOutgoingPreparation.Result.Ready
        val pdu = prepared.pdu
        // Compression may take time: observe revocation/SIM changes again before any effects.
        authorizationFailure()?.let { return SendResult(false, it, subscriptionId) }
        if (!runCatching { subscriptionId in effects.activeSubscriptionIds() }.getOrDefault(false)) {
            return SendResult(false, "REQUESTED_SUBSCRIPTION_NOT_ACTIVE", subscriptionId)
        }
        val currentLimit = runCatching { carrier.maxPduBytes }.getOrDefault(0)
        if (currentLimit <= 0) return SendResult(false, "MMS_CARRIER_CONFIG_UNAVAILABLE", subscriptionId)
        if (pdu.isEmpty() || pdu.size > minOf(maxPduBytes, currentLimit)) {
            return SendResult(false, "MMS_CARRIER_SIZE_EXCEEDED", subscriptionId)
        }
        val staged = try { effects.stage(pdu) } catch (_: Exception) {
            return SendResult(false, "MMS_PDU_STAGE_FAILED", subscriptionId)
        }
        if (staged !is MmsSendPduStager.Result.Staged) {
            return SendResult(false, (staged as MmsSendPduStager.Result.Rejected).reason)
        }

        val providerStore = effects
        runCatching { providerStore.repairJournal() }.onFailure {
            LocalLogger(context).log(
                LocalLogger.LogLevel.WARNING,
                "MmsProvider",
                "Réparation provider MMS différée; le nouvel envoi reste soumis à sa propre transaction"
            )
        }
        val providerAttachments = prepared.attachments.map {
            MmsConversationStore.Attachment(it.mimeType.lowercase(), it.payload)
        }
        val providerResult = try { providerStore.persistOutgoingOutbox(
            token = staged.token,
            transactionId = transactionId,
            destination = normalizedDestination,
            text = text,
            attachments = providerAttachments,
            subscriptionId = subscriptionId
        ) } catch (_: Exception) {
            runCatching { effects.delete(staged.fileName) }
            LocalLogger(context).log(LocalLogger.LogLevel.WARNING, "MmsProvider",
                "Projection MMS interrompue; aucun transport tenté, journal conservé pour récupération")
            return SendResult(false, "MMS_PROVIDER_PERSISTENCE_UNCERTAIN", subscriptionId, staged.token)
        }
        val providerMessageId = when (providerResult) {
            is MmsConversationStore.PersistResult.Ready -> providerResult.providerMessageId
            is MmsConversationStore.PersistResult.Rejected -> {
                runCatching { effects.delete(staged.fileName) }
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

            authorizationFailure()?.let { throw BeforeTransportRefused(it) }
            if (!runCatching { subscriptionId in effects.activeSubscriptionIds() }.getOrDefault(false)) {
                throw BeforeTransportRefused("REQUESTED_SUBSCRIPTION_NOT_ACTIVE")
            }
            val finalLimit = runCatching { carrier.maxPduBytes }.getOrDefault(0)
            if (finalLimit <= 0) throw BeforeTransportRefused("MMS_CARRIER_CONFIG_UNAVAILABLE")
            if (pdu.size > finalLimit) throw BeforeTransportRefused("MMS_CARRIER_SIZE_EXCEEDED")

            // From this instruction onward, a synchronous exception cannot prove that the platform
            // accepted no MMS bytes. Only callbacks may resolve that uncertainty.
            transportInvocationStarted = true
            carrier.send(staged.contentUri, callback)
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
        } catch (failure: Exception) {
            if (transportInvocationStarted) {
                // The Android telephony call was entered. Keep both OUTBOX and staged PDU because
                // a synchronous exception does not prove Android stopped consuming the content URI.
                // The callback or the bounded cache-prune path owns PDU cleanup from this point.
                if (!runCatching { providerStore.markSubmissionUnknown(staged.token, providerMessageId) }.getOrDefault(false)) {
                    LocalLogger(context).log(
                        LocalLogger.LogLevel.WARNING,
                        "MmsProvider",
                        "Issue de soumission MMS inconnue et journal SUBMISSION_UNKNOWN non confirmé"
                    )
                }
                SendResult(
                    accepted = false,
                    reason = "MMS_SUBMISSION_OUTCOME_UNKNOWN",
                    subscriptionId = subscriptionId,
                    token = staged.token,
                    providerMessageId = providerMessageId
                )
            } else {
                runCatching { effects.delete(staged.fileName) }
                // No transport invocation happened. Compensate the provider row instead of leaving
                // a fake pending message. If cleanup cannot be proven the recovery journal remains.
                val cleanupConfirmed = runCatching {
                    providerStore.abandonBeforeTransport(staged.token, providerMessageId)
                }.getOrDefault(false)
                if (!cleanupConfirmed) {
                    LocalLogger(context).log(
                        LocalLogger.LogLevel.WARNING,
                        "MmsProvider",
                        "Préparation transport MMS échouée; projection locale de l’échec à reprendre"
                    )
                }
                SendResult(
                    accepted = false,
                    reason = if (failure is BeforeTransportRefused) failure.reason else "MMS_SUBMISSION_PREPARATION_FAILED",
                    subscriptionId = subscriptionId,
                    token = staged.token,
                    providerMessageId = providerMessageId
                )
            }
        }
    }

    private class BeforeTransportRefused(val reason: String) : RuntimeException()

    private fun authorizationFailure(): String? = try {
        when {
            !effects.roleHeld() -> "SMS_ROLE_NOT_HELD"
            !effects.permissionGranted(Manifest.permission.SEND_SMS) || !effects.permissionEffective(Manifest.permission.SEND_SMS) -> "SEND_SMS_PERMISSION_NOT_GRANTED"
            !effects.permissionGranted(Manifest.permission.READ_PHONE_STATE) || !effects.permissionEffective(Manifest.permission.READ_PHONE_STATE) -> "READ_PHONE_STATE_PERMISSION_NOT_GRANTED"
            else -> null
        }
    } catch (_: Exception) { "SMS_PERMISSION_CHECK_FAILED" }

    internal interface Carrier {
        val maxPduBytes: Int
        fun send(uri: Uri, callback: PendingIntent)
    }

    /** The external-effect boundary; tests substitute it without invoking real telephony/provider. */
    internal interface Effects {
        fun hasMessagingFeature(): Boolean
        fun roleHeld(): Boolean
        fun permissionGranted(permission: String): Boolean
        fun permissionEffective(permission: String): Boolean
        fun activeSubscriptionIds(): Set<Int>
        fun defaultSubscriptionId(): Int?
        fun carrier(subscriptionId: Int): Carrier
        fun prepare(destination: String, transactionId: String, text: String, attachments: List<SentinelMmsSendPduComposer.Part>, maxPduBytes: Int): MmsOutgoingPreparation.Result =
            MmsOutgoingPreparation.prepare(destination, transactionId, text, attachments, maxPduBytes)
        fun stage(pdu: ByteArray): MmsSendPduStager.Result
        fun delete(fileName: String): Boolean
        fun repairJournal(): Int
        fun persistOutgoingOutbox(token: String, transactionId: String, destination: String, text: String, attachments: List<MmsConversationStore.Attachment>, subscriptionId: Int): MmsConversationStore.PersistResult
        fun markSubmitted(token: String, providerMessageId: Long): Boolean
        fun markSubmissionUnknown(token: String, providerMessageId: Long): Boolean
        fun abandonBeforeTransport(token: String, providerMessageId: Long): Boolean
    }

    private class AndroidEffects(private val context: Context) : Effects {
        private val store by lazy { MmsConversationStore(context) }
        override fun hasMessagingFeature() = context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_MESSAGING)
        override fun roleHeld() = context.readSmsRoleStateFailClosed() == SmsActivationDiagnostics.SmsRoleState.HELD
        override fun permissionGranted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        override fun permissionEffective(permission: String) = PermissionChecker.checkSelfPermission(context, permission) == PermissionChecker.PERMISSION_GRANTED
        override fun activeSubscriptionIds(): Set<Int> = try {
            context.getSystemService(SubscriptionManager::class.java)
                .activeSubscriptionInfoList.orEmpty().map { it.subscriptionId }
                .filter { it != SubscriptionManager.INVALID_SUBSCRIPTION_ID }.toSet()
        } catch (_: SecurityException) {
            // Permission can be revoked between the sender gate and this platform call.
            emptySet()
        }
        override fun defaultSubscriptionId() = SubscriptionManager.getDefaultSmsSubscriptionId()
            .takeUnless { it == SubscriptionManager.INVALID_SUBSCRIPTION_ID }
        override fun carrier(subscriptionId: Int): Carrier {
            @Suppress("DEPRECATION")
            val manager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java).createForSubscriptionId(subscriptionId)
            } else SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
            return object : Carrier {
                override val maxPduBytes: Int get() = manager.carrierConfigValues.getInt(SmsManager.MMS_CONFIG_MAX_MESSAGE_SIZE, 0)
                override fun send(uri: Uri, callback: PendingIntent) = manager.sendMultimediaMessage(context, uri, null, null, callback)
            }
        }
        override fun stage(pdu: ByteArray) = MmsSendPduStager.stage(context, pdu)
        override fun delete(fileName: String) = MmsSendPduStager.delete(context, fileName)
        override fun repairJournal() = store.repairJournal()
        override fun persistOutgoingOutbox(token: String, transactionId: String, destination: String, text: String, attachments: List<MmsConversationStore.Attachment>, subscriptionId: Int) =
            store.persistOutgoingOutbox(token, transactionId, destination, text, attachments, subscriptionId)
        override fun markSubmitted(token: String, providerMessageId: Long) = store.markSubmitted(token, providerMessageId)
        override fun markSubmissionUnknown(token: String, providerMessageId: Long) = store.markSubmissionUnknown(token, providerMessageId)
        override fun abandonBeforeTransport(token: String, providerMessageId: Long) = store.abandonBeforeTransport(token, providerMessageId)
    }

    companion object {
        const val ACTION_SENT = "com.sentinel.quantum.MMS_SENT_RESULT"
        const val EXTRA_FILE_NAME = "mms.send.file"
        const val EXTRA_SUBSCRIPTION_ID = "mms.send.subscription"
        const val EXTRA_PROVIDER_MESSAGE_ID = "mms.send.provider_message_id"
    }
}
