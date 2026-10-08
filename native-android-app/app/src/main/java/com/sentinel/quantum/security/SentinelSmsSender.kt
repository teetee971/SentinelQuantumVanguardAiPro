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
        val conversations = SmsConversationStore(context)
        val persistedMessageId = conversations.insertOutgoingOutbox(
            normalized,
            body,
            prepared.subscriptionId
        ) ?: return SendResult(false, "OUTGOING_PROVIDER_PERSIST_FAILED")

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
            val repaired = conversations.markOutgoingFailed(persistedMessageId)
            return SendResult(
                accepted = false,
                reason = SmsSubmissionOutcomePolicy.reasonForCallbackPreparationException(repaired),
                subscriptionId = prepared.subscriptionId,
                providerMessageId = persistedMessageId,
                partCount = parts.size
            )
        }

        val watchdogReady = runCatching {
            val registered = SmsOutgoingSubmissionStore(context).register(
                sendToken = callbacks.sendToken,
                providerMessageId = persistedMessageId,
                partCount = parts.size
            )
            check(registered) { "SMS submission ledger unavailable" }
            SmsSubmissionWatchdogWorker.schedule(context)
            true
        }.getOrDefault(false)
        if (!watchdogReady) {
            SmsOutgoingSubmissionStore(context).remove(callbacks.sendToken, persistedMessageId)
            conversations.markOutgoingFailed(persistedMessageId)
            return SendResult(
                accepted = false,
                reason = "SMS_SUBMISSION_WATCHDOG_UNAVAILABLE",
                subscriptionId = prepared.subscriptionId,
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
            SendResult(
                accepted = true,
                reason = "SUBMITTED_TO_ANDROID_TELEPHONY",
                subscriptionId = prepared.subscriptionId,
                sendToken = callbacks.sendToken,
                providerMessageId = persistedMessageId,
                partCount = parts.size
            )
        } catch (_: Exception) {
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
