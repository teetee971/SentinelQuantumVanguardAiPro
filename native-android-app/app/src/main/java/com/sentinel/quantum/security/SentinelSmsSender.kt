package com.sentinel.quantum.security

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.PhoneNumberUtils
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import java.security.SecureRandom
import java.util.UUID

/**
 * Real SMS sending primitive for the user-selected default-SMS client.
 * It refuses to send unless Sentinel actually holds ROLE_SMS and the effective Android
 * authorization (runtime permission plus associated AppOp, when defined) allows the operation.
 */
class SentinelSmsSender(private val context: Context) {

    data class SendResult(
        val accepted: Boolean,
        val reason: String,
        val subscriptionId: Int? = null,
        val sendToken: Int? = null,
        val providerMessageId: Long? = null,
        val partCount: Int? = null
    )

    private data class PreparedSubmission(
        val subscriptionId: Int,
        val manager: SmsManager,
        val parts: ArrayList<String>
    )

    private data class PreparedCallbacks(
        val sendToken: Int,
        val sent: ArrayList<PendingIntent>,
        val delivered: ArrayList<PendingIntent>
    )

    fun send(destination: String, body: String, requestedSubscriptionId: Int? = null): SendResult {
        val normalized = sanitizeDestination(destination) ?: return SendResult(false, "INVALID_DESTINATION")
        if (body.isBlank() || body.length > MAX_BODY_CHARS) {
            return SendResult(false, "INVALID_MESSAGE")
        }
        if (!holdsSmsRole()) return SendResult(false, "SMS_ROLE_NOT_HELD")
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            return SendResult(false, "SEND_SMS_PERMISSION_NOT_GRANTED")
        }
        if (!hasEffectivePermission(Manifest.permission.SEND_SMS)) {
            return SendResult(false, "SEND_SMS_PERMISSION_NOT_GRANTED")
        }
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            return SendResult(false, "READ_PHONE_STATE_PERMISSION_NOT_GRANTED")
        }
        if (!hasEffectivePermission(Manifest.permission.READ_PHONE_STATE)) {
            return SendResult(false, "READ_PHONE_STATE_PERMISSION_NOT_GRANTED")
        }
        when (emergencyNumberState(normalized)) {
            EmergencyNumberState.EMERGENCY ->
                return SendResult(false, "EMERGENCY_NUMBER_USE_DIALER")
            EmergencyNumberState.LOOKUP_FAILED ->
                return SendResult(false, "EMERGENCY_NUMBER_CHECK_FAILED")
            EmergencyNumberState.NOT_EMERGENCY -> Unit
        }

        // Everything in this phase happens before Sentinel creates an OUTBOX row and before any
        // SmsManager send call. A failure here is conclusively a preparation failure, not an
        // unknown telephony submission outcome.
        val prepared = try {
            val subscriptionManager = context.getSystemService(SubscriptionManager::class.java)
            val activeIds = runCatching {
                subscriptionManager.activeSubscriptionInfoList
                    .orEmpty()
                    .map { it.subscriptionId }
                    .filter { it != SubscriptionManager.INVALID_SUBSCRIPTION_ID }
                    .toSet()
            }.getOrElse { return SendResult(false, "SMS_SUBSCRIPTION_LOOKUP_FAILED") }
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

            @Suppress("DEPRECATION")
            val manager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
                    .createForSubscriptionId(subscriptionId)
            } else {
                SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
            }

            val parts = manager.divideMessage(body)
            SmsSubmissionOutcomePolicy.reasonForPartCount(parts.size)?.let { reason ->
                return SendResult(false, reason)
            }
            PreparedSubmission(subscriptionId, manager, parts)
        } catch (_: Exception) {
            return SendResult(false, SmsSubmissionOutcomePolicy.reasonForPreparationException())
        }

        val parts = prepared.parts
        val preSubmitJournal = SmsPreSubmitJournal(context)
        val preSubmitToken = UUID.randomUUID().toString()
        val providerPreparedAtMs = System.currentTimeMillis()
        if (!preSubmitJournal.begin(preSubmitToken, prepared.subscriptionId, providerPreparedAtMs)) {
            return SendResult(false, PRE_SUBMIT_JOURNAL_UNAVAILABLE)
        }

        val persistedMessageId = SmsPreSubmitProvider.insertOutgoingOutbox(
            context = context,
            address = normalized,
            body = body,
            subscriptionId = prepared.subscriptionId,
            timestampMs = providerPreparedAtMs
        ) ?: run {
            preSubmitJournal.remove(preSubmitToken)
            return SendResult(false, "OUTGOING_PROVIDER_PERSIST_FAILED")
        }
        if (!preSubmitJournal.recordProvider(preSubmitToken, persistedMessageId)) {
            val repaired = SmsPreSubmitProvider.markOutgoingFailed(context, persistedMessageId)
            if (repaired) preSubmitJournal.remove(preSubmitToken)
            return SendResult(
                accepted = false,
                reason = if (repaired) {
                    PRE_SUBMIT_JOURNAL_PROVIDER_CORRELATION_FAILED
                } else {
                    PRE_SUBMIT_REVALIDATION_PROVIDER_REPAIR_FAILED
                },
                subscriptionId = prepared.subscriptionId,
                providerMessageId = persistedMessageId,
                partCount = parts.size
            )
        }

        // PendingIntent construction is still pre-submission. If it fails, the modem has not been
        // called and the durable OUTBOX row must be repaired to FAILED rather than left as SENDING.
        val callbacks = try {
            val sendToken = nextRequestToken()
            fun statusIntent(action: String, partIndex: Int, delivered: Boolean): PendingIntent {
                val callbackKind = if (delivered) "delivered" else "sent"
                return PendingIntent.getBroadcast(
                    context,
                    requestCode(sendToken, partIndex, delivered),
                    Intent(context, SentinelSmsStatusReceiver::class.java)
                        .setAction(action)
                        .setData(
                            Uri.parse(
                                "sentinel-sms-status://callback/" +
                                    "$sendToken/$persistedMessageId/$partIndex/${parts.size}/$callbackKind"
                            )
                        )
                        .putExtra(EXTRA_SEND_TOKEN, sendToken)
                        .putExtra(EXTRA_PART_INDEX, partIndex)
                        .putExtra(EXTRA_PART_COUNT, parts.size)
                        .putExtra(EXTRA_PROVIDER_MESSAGE_ID, persistedMessageId),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }

            val sent = ArrayList<PendingIntent>(parts.size)
            val delivered = ArrayList<PendingIntent>(parts.size)
            for (partIndex in parts.indices) {
                sent += statusIntent(ACTION_SENT, partIndex, false)
                delivered += statusIntent(ACTION_DELIVERED, partIndex, true)
            }
            PreparedCallbacks(sendToken, sent, delivered)
        } catch (_: Exception) {
            val repaired = SmsPreSubmitProvider.markOutgoingFailed(context, persistedMessageId)
            if (repaired) preSubmitJournal.remove(preSubmitToken)
            return SendResult(
                accepted = false,
                reason = SmsSubmissionOutcomePolicy.reasonForCallbackPreparationException(repaired),
                subscriptionId = prepared.subscriptionId,
                providerMessageId = persistedMessageId,
                partCount = parts.size
            )
        }

        // Instrumentation can flip AppOps at this exact boundary. Release builds execute a no-op.
        SmsPreTransportTestInterlock.beforeFinalAuthorizationRecheck()

        // Authorization and SIM state can change after the initial checks while provider/callback
        // preparation is running. Re-read the complete send-critical state at the last safe point,
        // before crossing the SmsManager submission boundary. A failure here is conclusively
        // "not submitted", so repair the OUTBOX row instead of returning an unknown outcome.
        val preSubmitFailure = revalidateBeforeSubmission(prepared.subscriptionId)
        if (preSubmitFailure != null) {
            val repaired = SmsPreSubmitProvider.markOutgoingFailed(context, persistedMessageId)
            if (repaired) preSubmitJournal.remove(preSubmitToken)
            return SendResult(
                accepted = false,
                reason = if (repaired) preSubmitFailure else PRE_SUBMIT_REVALIDATION_PROVIDER_REPAIR_FAILED,
                subscriptionId = prepared.subscriptionId,
                sendToken = callbacks.sendToken,
                providerMessageId = persistedMessageId,
                partCount = parts.size
            )
        }

        // Persist ambiguity before entering SmsManager. If the process dies after this commit,
        // recovery must preserve the provider row rather than inventing a definite send failure.
        if (!preSubmitJournal.markTransportStarted(preSubmitToken, persistedMessageId)) {
            val repaired = SmsPreSubmitProvider.markOutgoingFailed(context, persistedMessageId)
            if (repaired) preSubmitJournal.remove(preSubmitToken)
            return SendResult(
                accepted = false,
                reason = if (repaired) {
                    PRE_SUBMIT_JOURNAL_TRANSPORT_MARK_FAILED
                } else {
                    PRE_SUBMIT_REVALIDATION_PROVIDER_REPAIR_FAILED
                },
                subscriptionId = prepared.subscriptionId,
                sendToken = callbacks.sendToken,
                providerMessageId = persistedMessageId,
                partCount = parts.size
            )
        }

        // Only this call boundary can have an indeterminate synchronous outcome: SmsManager may
        // throw after Android has accepted one or more segments. Keep OUTBOX/PENDING in that case;
        // validated SENT callbacks remain the only conclusive durable transition.
        return try {
            if (parts.size <= 1) {
                prepared.manager.sendTextMessage(
                    normalized,
                    null,
                    body,
                    callbacks.sent.first(),
                    callbacks.delivered.first()
                )
            } else {
                prepared.manager.sendMultipartTextMessage(
                    normalized,
                    null,
                    ArrayList(parts),
                    callbacks.sent,
                    callbacks.delivered
                )
            }
            preSubmitJournal.remove(preSubmitToken)
            SendResult(
                accepted = true,
                reason = "SUBMITTED_TO_ANDROID_TELEPHONY",
                subscriptionId = prepared.subscriptionId,
                sendToken = callbacks.sendToken,
                providerMessageId = persistedMessageId,
                partCount = parts.size
            )
        } catch (_: Exception) {
            // TRANSPORT_STARTED deliberately remains durable: a synchronous exception cannot prove
            // whether Android accepted one or more segments before throwing.
            SendResult(
                accepted = false,
                reason = SmsSubmissionOutcomePolicy.reasonForSubmissionException(),
                subscriptionId = prepared.subscriptionId,
                sendToken = callbacks.sendToken,
                providerMessageId = persistedMessageId,
                partCount = parts.size
            )
        }
    }

    private fun revalidateBeforeSubmission(subscriptionId: Int): String? {
        if (!holdsSmsRole()) return "SMS_ROLE_NOT_HELD"
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) !=
                PackageManager.PERMISSION_GRANTED ||
            !hasEffectivePermission(Manifest.permission.SEND_SMS)
        ) {
            return "SEND_SMS_PERMISSION_NOT_GRANTED"
        }
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) !=
                PackageManager.PERMISSION_GRANTED ||
            !hasEffectivePermission(Manifest.permission.READ_PHONE_STATE)
        ) {
            return "READ_PHONE_STATE_PERMISSION_NOT_GRANTED"
        }

        val activeIds = try {
            context.getSystemService(SubscriptionManager::class.java)
                .activeSubscriptionInfoList
                .orEmpty()
                .map { it.subscriptionId }
                .filter { it != SubscriptionManager.INVALID_SUBSCRIPTION_ID }
                .toSet()
        } catch (_: SecurityException) {
            return "SMS_SUBSCRIPTION_LOOKUP_FAILED"
        } catch (_: RuntimeException) {
            return "SMS_SUBSCRIPTION_LOOKUP_FAILED"
        }
        if (subscriptionId !in activeIds) return "REQUESTED_SUBSCRIPTION_NOT_ACTIVE"
        return null
    }

    private fun hasEffectivePermission(permission: String): Boolean =
        PermissionChecker.checkSelfPermission(context, permission) == PermissionChecker.PERMISSION_GRANTED

    private fun sanitizeDestination(raw: String): String? {
        val value = raw.trim()
        if (value.isEmpty() || value.length > 32) return null
        if (value.count { it == '+' } > 1 || ('+' in value && !value.startsWith("+"))) return null
        if (!value.all { it.isDigit() || it in "+*#" }) return null
        return value
    }

    private enum class EmergencyNumberState { EMERGENCY, NOT_EMERGENCY, LOOKUP_FAILED }

    private fun emergencyNumberState(number: String): EmergencyNumberState {
        return try {
            val emergency = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.getSystemService(TelephonyManager::class.java).isEmergencyNumber(number)
            } else {
                @Suppress("DEPRECATION")
                PhoneNumberUtils.isEmergencyNumber(number)
            }
            if (emergency) EmergencyNumberState.EMERGENCY else EmergencyNumberState.NOT_EMERGENCY
        } catch (_: SecurityException) {
            EmergencyNumberState.LOOKUP_FAILED
        } catch (_: RuntimeException) {
            EmergencyNumberState.LOOKUP_FAILED
        }
    }

    fun holdsSmsRole(): Boolean =
        context.readSmsRoleStateFailClosed() == SmsActivationDiagnostics.SmsRoleState.HELD

    companion object {
        const val ACTION_SENT = "com.sentinel.quantum.SMS_SENT"
        const val ACTION_DELIVERED = "com.sentinel.quantum.SMS_DELIVERED"
        const val MAX_BODY_CHARS = 20_000
        const val EXTRA_SEND_TOKEN = "sms.send_token"
        const val EXTRA_PART_INDEX = "sms.part_index"
        const val EXTRA_PART_COUNT = "sms.part_count"
        const val EXTRA_PROVIDER_MESSAGE_ID = "sms.provider_message_id"
        const val PRE_SUBMIT_REVALIDATION_PROVIDER_REPAIR_FAILED =
            "SMS_PRE_SUBMIT_REVALIDATION_FAILED_PROVIDER_REPAIR_FAILED"
        const val PRE_SUBMIT_JOURNAL_UNAVAILABLE = "SMS_PRE_SUBMIT_JOURNAL_UNAVAILABLE"
        const val PRE_SUBMIT_JOURNAL_PROVIDER_CORRELATION_FAILED =
            "SMS_PRE_SUBMIT_JOURNAL_PROVIDER_CORRELATION_FAILED"
        const val PRE_SUBMIT_JOURNAL_TRANSPORT_MARK_FAILED =
            "SMS_PRE_SUBMIT_JOURNAL_TRANSPORT_MARK_FAILED"
        private val requestTokenRandom = SecureRandom()

        /**
         * Process-local counters restart after process death and can therefore alias a still-pending
         * SENT/DELIVERED PendingIntent from the previous process. A positive cryptographic random
         * token keeps callback identities independent across process lifetimes.
         */
        private fun nextRequestToken(): Int = requestTokenRandom.nextInt(Int.MAX_VALUE - 1) + 1

        private fun requestCode(token: Int, partIndex: Int, delivered: Boolean): Int {
            var value = 31 * token + partIndex
            if (delivered) value = value xor 0x5a5a5a5a
            return value
        }
    }
}
