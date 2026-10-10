package com.sentinel.quantum.security

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.Telephony

/** Provider operations used only before the SmsManager transport boundary. */
internal object SmsPreSubmitProvider {
    enum class PreparedOutboxRepair { ABSENT, REPAIRED, AMBIGUOUS, FAILED }

    fun insertOutgoingOutbox(
        context: Context,
        address: String,
        body: String,
        subscriptionId: Int,
        timestampMs: Long
    ): Long? {
        if (
            context.readSmsRoleStateFailClosed() !=
                SmsActivationDiagnostics.SmsRoleState.HELD
        ) return null
        val safeAddress = address.trim()
        if (
            safeAddress.isEmpty() || safeAddress.length > MAX_ADDRESS_CHARS ||
            !safeAddress.all { it.isDigit() || it in "+*#" } ||
            body.isBlank() || body.length > SentinelSmsSender.MAX_BODY_CHARS ||
            subscriptionId < 0 || timestampMs < 0L
        ) return null

        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, safeAddress)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, timestampMs)
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
            put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_PENDING)
            put(Telephony.Sms.SUBSCRIPTION_ID, subscriptionId)
            put(Telephony.Sms.CREATOR, context.packageName)
        }
        return runCatching {
            context.contentResolver.insert(Telephony.Sms.Outbox.CONTENT_URI, values)
                ?.let(ContentUris::parseId)
                ?.takeIf { it > 0L }
        }.getOrNull()
    }

    fun markOutgoingFailed(context: Context, providerMessageId: Long): Boolean {
        if (
            context.readSmsRoleStateFailClosed() !=
                SmsActivationDiagnostics.SmsRoleState.HELD ||
            providerMessageId <= 0L
        ) return false
        val uri = ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, providerMessageId)
        val values = ContentValues().apply {
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_FAILED)
            put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_FAILED)
        }
        return runCatching {
            context.contentResolver.update(uri, values, null, null) == 1
        }.getOrDefault(false)
    }

    fun repairPreparedOutbox(
        context: Context,
        timestampMs: Long,
        subscriptionId: Int
    ): PreparedOutboxRepair {
        if (
            context.readSmsRoleStateFailClosed() !=
                SmsActivationDiagnostics.SmsRoleState.HELD ||
            timestampMs < 0L || subscriptionId < 0
        ) return PreparedOutboxRepair.FAILED

        val ids = runCatching {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms._ID, Telephony.Sms.CREATOR),
                "${Telephony.Sms.DATE}=? AND ${Telephony.Sms.SUBSCRIPTION_ID}=? AND " +
                    "${Telephony.Sms.TYPE}=? AND ${Telephony.Sms.STATUS}=?",
                arrayOf(
                    timestampMs.toString(),
                    subscriptionId.toString(),
                    Telephony.Sms.MESSAGE_TYPE_OUTBOX.toString(),
                    Telephony.Sms.STATUS_PENDING.toString()
                ),
                null
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
                val creatorIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.CREATOR)
                buildList {
                    while (cursor.moveToNext() && size < 2) {
                        if (cursor.getString(creatorIndex) != context.packageName) continue
                        cursor.getLong(idIndex).takeIf { it > 0L }?.let(::add)
                    }
                }
            } ?: emptyList()
        }.getOrElse { return PreparedOutboxRepair.FAILED }

        return when (ids.size) {
            0 -> PreparedOutboxRepair.ABSENT
            1 -> if (markOutgoingFailed(context, ids.single())) {
                PreparedOutboxRepair.REPAIRED
            } else {
                PreparedOutboxRepair.FAILED
            }
            else -> PreparedOutboxRepair.AMBIGUOUS
        }
    }

    private const val MAX_ADDRESS_CHARS = 128
}
