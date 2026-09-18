package com.sentinel.quantum.sms

import android.Manifest
import android.app.role.RoleManager
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.core.content.ContextCompat

object SmsProviderStore {
    data class Message(
        val id: Long,
        val address: String,
        val body: String,
        val timestamp: Long,
        val type: Int
    )

    fun isDefaultSmsHandler(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roles = context.getSystemService(RoleManager::class.java)
            roles?.isRoleHeld(RoleManager.ROLE_SMS) == true
        } else {
            Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
        }
    }

    fun hasRuntimeSmsPermissions(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    fun insertIncomingSms(
        context: Context,
        address: String,
        body: String,
        timestamp: Long = System.currentTimeMillis(),
        subscriptionId: Int? = null
    ): Boolean {
        if (!isDefaultSmsHandler(context)) return false
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address.take(128))
            put(Telephony.Sms.BODY, body.take(MAX_BODY_LENGTH))
            put(Telephony.Sms.DATE, timestamp)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
            subscriptionId?.let { put("sub_id", it) }
        }
        return runCatching {
            context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values) != null
        }.getOrDefault(false)
    }

    fun insertSentSms(
        context: Context,
        address: String,
        body: String,
        timestamp: Long = System.currentTimeMillis()
    ): Boolean {
        if (!isDefaultSmsHandler(context)) return false
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address.take(128))
            put(Telephony.Sms.BODY, body.take(MAX_BODY_LENGTH))
            put(Telephony.Sms.DATE, timestamp)
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
        }
        return runCatching {
            context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values) != null
        }.getOrDefault(false)
    }

    fun sendText(context: Context, address: String, body: String, subscriptionId: Int? = null): Boolean {
        if (!isDefaultSmsHandler(context) || !hasRuntimeSmsPermissions(context)) return false
        val destination = address.trim().takeIf { it.length in 3..64 } ?: return false
        val message = body.trim().takeIf { it.isNotEmpty() && it.length <= MAX_BODY_LENGTH } ?: return false
        return runCatching {
            @Suppress("DEPRECATION")
            val manager = if (subscriptionId != null && subscriptionId >= 0) {
                SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
            } else {
                SmsManager.getDefault()
            }
            val parts = manager.divideMessage(message)
            if (parts.size <= 1) {
                manager.sendTextMessage(destination, null, message, null, null)
            } else {
                manager.sendMultipartTextMessage(destination, null, ArrayList(parts), null, null)
            }
            insertSentSms(context, destination, message)
            true
        }.getOrDefault(false)
    }

    fun recentMessages(context: Context, limit: Int = 100): List<Message> {
        if (!isDefaultSmsHandler(context) ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED
        ) return emptyList()
        val bounded = limit.coerceIn(1, 200)
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE
        )
        return runCatching {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                null,
                null,
                "${Telephony.Sms.DATE} DESC LIMIT $bounded"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
                val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val typeIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            Message(
                                id = cursor.getLong(idIndex),
                                address = cursor.getString(addressIndex).orEmpty().take(128),
                                body = cursor.getString(bodyIndex).orEmpty().take(MAX_BODY_LENGTH),
                                timestamp = cursor.getLong(dateIndex),
                                type = cursor.getInt(typeIndex)
                            )
                        )
                    }
                }
            } ?: emptyList()
        }.getOrDefault(emptyList())
    }

    const val MAX_BODY_LENGTH = 10_000
}
