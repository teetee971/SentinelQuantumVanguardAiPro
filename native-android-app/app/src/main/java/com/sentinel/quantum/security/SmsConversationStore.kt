package com.sentinel.quantum.security

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

/**
 * Role-gated access to the Android SMS provider for the staged default-SMS client.
 *
 * Reads, deletes and exports are only allowed while Sentinel is the default SMS handler and
 * READ_SMS is granted. Exports stay in an app cache directory exposed only through FileProvider.
 */
class SmsConversationStore(private val context: Context) {

    data class Message(
        val id: Long,
        val address: String,
        val body: String,
        val timestampMs: Long,
        val type: Int,
        val threadId: Long
    )

    data class ThreadSummary(
        val threadId: Long,
        val address: String,
        val latestBody: String,
        val latestTimestampMs: Long,
        val messageCount: Int
    )

    data class ExportResult(
        val uri: Uri,
        val messageCount: Int
    )

    fun recentMessages(limit: Int = 100): List<Message> {
        if (!canRead()) return emptyList()
        val bounded = limit.coerceIn(1, MAX_MESSAGES)
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
            Telephony.Sms.THREAD_ID
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
                val threadIdIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            Message(
                                id = cursor.getLong(idIndex),
                                address = cursor.getString(addressIndex).orEmpty().take(MAX_ADDRESS_CHARS),
                                body = cursor.getString(bodyIndex).orEmpty().take(SentinelSmsSender.MAX_BODY_CHARS),
                                timestampMs = cursor.getLong(dateIndex),
                                type = cursor.getInt(typeIndex),
                                threadId = cursor.getLong(threadIdIndex)
                            )
                        )
                    }
                }
            } ?: emptyList()
        }.getOrDefault(emptyList())
    }

    fun recentThreads(limit: Int = 50): List<ThreadSummary> {
        if (!canRead()) return emptyList()
        val bounded = limit.coerceIn(1, MAX_THREADS)
        val messages = recentMessages(MAX_MESSAGES)
        return messages
            .groupBy { message -> message.threadId }
            .filterKeys { it > 0L }
            .map { (threadId, threadMessages) ->
                val latest = threadMessages.maxBy { it.timestampMs }
                ThreadSummary(
                    threadId = threadId,
                    address = latest.address,
                    latestBody = latest.body,
                    latestTimestampMs = latest.timestampMs,
                    messageCount = threadMessages.size
                )
            }
            .sortedByDescending { it.latestTimestampMs }
            .take(bounded)
    }

    fun messagesForThread(threadId: Long, limit: Int = 100): List<Message> {
        if (!canRead() || threadId <= 0L) return emptyList()
        val bounded = limit.coerceIn(1, MAX_MESSAGES)
        return queryMessages(
            selection = "${Telephony.Sms.THREAD_ID}=?",
            selectionArgs = arrayOf(threadId.toString()),
            sortOrder = "${Telephony.Sms.DATE} DESC LIMIT $bounded"
        ).reversed()
    }

    private fun queryMessages(selection: String?, selectionArgs: Array<String>?, sortOrder: String): List<Message> {
        val projection = arrayOf(
            Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY,
            Telephony.Sms.DATE, Telephony.Sms.TYPE, Telephony.Sms.THREAD_ID
        )
        return runCatching {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI, projection, selection, selectionArgs, sortOrder
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
                val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val typeIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                val threadIdIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
                buildList {
                    while (cursor.moveToNext()) add(
                        Message(
                            cursor.getLong(idIndex),
                            cursor.getString(addressIndex).orEmpty().take(MAX_ADDRESS_CHARS),
                            cursor.getString(bodyIndex).orEmpty().take(SentinelSmsSender.MAX_BODY_CHARS),
                            cursor.getLong(dateIndex),
                            cursor.getInt(typeIndex),
                            cursor.getLong(threadIdIndex)
                        )
                    )
                }
            } ?: emptyList()
        }.getOrDefault(emptyList())
    }

    fun deleteMessage(id: Long): Boolean {
        if (!canRead() || id <= 0L) return false
        val uri = Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, id.toString())
        return runCatching {
            context.contentResolver.delete(uri, null, null) == 1
        }.getOrDefault(false)
    }

    fun exportRecentMessages(limit: Int = 100): ExportResult? {
        if (!canRead()) return null
        val messages = recentMessages(limit)
        if (messages.isEmpty()) return null

        val array = JSONArray()
        messages.forEach { message ->
            array.put(
                JSONObject()
                    .put("id", message.id)
                    .put("address", message.address)
                    .put("body", message.body)
                    .put("timestamp_ms", message.timestampMs)
                    .put("type", message.type)
            )
        }

        val root = JSONObject()
            .put("schema_version", 1)
            .put("exported_at", Instant.now().toString())
            .put("message_count", messages.size)
            .put("messages", array)

        val directory = File(context.cacheDir, EXPORT_DIRECTORY)
        if (!directory.exists() && !directory.mkdirs()) return null
        directory.listFiles()?.forEach { file ->
            if (file.isFile && System.currentTimeMillis() - file.lastModified() > EXPORT_MAX_AGE_MS) {
                runCatching { file.delete() }
            }
        }

        val file = File(directory, "sentinel-sms-${System.currentTimeMillis()}.json")
        return runCatching {
            file.writeText(root.toString(2), Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                file
            )
            ExportResult(uri = uri, messageCount = messages.size)
        }.getOrNull()
    }

    fun canRead(): Boolean =
        holdsSmsRole() &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_SMS
            ) == PackageManager.PERMISSION_GRANTED

    private fun holdsSmsRole(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val manager = context.getSystemService(RoleManager::class.java)
            manager.isRoleAvailable(RoleManager.ROLE_SMS) &&
                manager.isRoleHeld(RoleManager.ROLE_SMS)
        } else {
            Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
        }
    }

    companion object {
        private const val MAX_MESSAGES = 200
        private const val MAX_THREADS = 50
        private const val MAX_ADDRESS_CHARS = 128
        private const val EXPORT_DIRECTORY = "sentinel_sms_export"
        private const val EXPORT_MAX_AGE_MS = 24L * 60L * 60L * 1000L
    }
}
