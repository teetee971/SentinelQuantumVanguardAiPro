package com.sentinel.quantum.sms

import android.app.role.RoleManager
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager

class SmsRepository(private val context: Context) {
    data class Message(
        val id: Long,
        val address: String,
        val body: String,
        val date: Long,
        val type: Int
    )

    fun isDefaultHandler(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getSystemService(RoleManager::class.java)
                .isRoleHeld(RoleManager.ROLE_SMS)
        } else {
            Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
        }

    fun recent(limit: Int = 100): List<Message> {
        if (!isDefaultHandler()) return emptyList()
        val boundedLimit = limit.coerceIn(1, 200)
        return runCatching {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(
                    Telephony.Sms._ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms.TYPE
                ),
                null,
                null,
                Telephony.Sms.DEFAULT_SORT_ORDER
            )?.use { cursor ->
                val id = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
                val address = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val body = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val date = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val type = cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                buildList {
                    while (cursor.moveToNext() && size < boundedLimit) {
                        add(
                            Message(
                                id = cursor.getLong(id),
                                address = cursor.getString(address).orEmpty().take(128),
                                body = cursor.getString(body).orEmpty().take(10_000),
                                date = cursor.getLong(date),
                                type = cursor.getInt(type)
                            )
                        )
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptyList())
    }

    fun send(address: String, body: String): Boolean {
        if (!isDefaultHandler()) return false
        val recipient = address.trim().take(64)
        val message = body.take(5_000)
        if (recipient.isBlank() || message.isBlank()) return false
        return runCatching {
            @Suppress("DEPRECATION")
            val manager = SmsManager.getDefault()
            val parts = manager.divideMessage(message)
            if (parts.size > 1) {
                manager.sendMultipartTextMessage(recipient, null, parts, null, null)
            } else {
                manager.sendTextMessage(recipient, null, message, null, null)
            }
            true
        }.getOrDefault(false)
    }

    fun storeIncoming(address: String?, body: String, timestamp: Long): Boolean {
        if (!isDefaultHandler()) return false
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address.orEmpty().take(128))
            put(Telephony.Sms.BODY, body.take(10_000))
            put(Telephony.Sms.DATE, timestamp)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
        }
        return runCatching {
            context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values) != null
        }.getOrDefault(false)
    }

    fun delete(id: Long): Boolean {
        if (!isDefaultHandler() || id <= 0L) return false
        return runCatching {
            context.contentResolver.delete(
                Telephony.Sms.CONTENT_URI,
                "${Telephony.Sms._ID}=?",
                arrayOf(id.toString())
            ) > 0
        }.getOrDefault(false)
    }
}
