package com.sentinel.quantum.security

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
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
 * Role-gated access to Android's canonical SMS/MMS provider conversation state.
 *
 * Reads and deletes are allowed only while Sentinel is the active default SMS handler and READ_SMS
 * is granted. SMS and MMS are merged by the Android thread_id. MMS list rendering reads only root,
 * address and text-part metadata; binary parts are never opened just to draw a conversation list.
 *
 * Export intentionally remains SMS-only until the export contract is extended to MMS explicitly.
 * Exports stay in an app cache directory exposed only through FileProvider.
 */
internal object SmsTimestampOrder {
    const val FUTURE_TOLERANCE_MS = 5L * 60L * 1000L

    fun latestPlausibleTimestamp(nowMs: Long): Long =
        if (nowMs > Long.MAX_VALUE - FUTURE_TOLERANCE_MS) Long.MAX_VALUE
        else nowMs + FUTURE_TOLERANCE_MS

    fun isAnomalous(originalDateMs: Long, nowMs: Long): Boolean =
        originalDateMs < 0L || originalDateMs > latestPlausibleTimestamp(nowMs)

    // Keep provider dates intact; anomalous dates must not outrank real recent messages.
    fun sortTimestamp(originalDateMs: Long, nowMs: Long): Long =
        if (isAnomalous(originalDateMs, nowMs)) Long.MIN_VALUE else originalDateMs

    /** Apply provider limits after separating plausible dates so hostile dates cannot fill the window. */
    fun <T> loadWindow(
        limit: Int,
        loadPlausible: (Int) -> List<T>,
        loadAnomalous: (Int) -> List<T>
    ): List<T> {
        require(limit > 0)
        val normal = loadPlausible(limit).take(limit)
        val remaining = limit - normal.size
        return if (remaining == 0) normal else normal + loadAnomalous(remaining).take(remaining)
    }
}

/** Pure mapping shared by provider reads and JVM tests. */
internal object UnifiedProviderMessage {
    private const val MMS_ID_NAMESPACE = -1L

    fun encodeMmsId(providerId: Long): Long? =
        providerId.takeIf { it > 0L && it < Long.MAX_VALUE }?.let { MMS_ID_NAMESPACE * it }

    fun decodeMmsId(messageId: Long): Long? =
        messageId.takeIf { it < 0L && it != Long.MIN_VALUE }?.let { -it }

    fun mmsDateMs(seconds: Long): Long = when {
        seconds < 0L -> -1L
        seconds > Long.MAX_VALUE / 1000L -> Long.MAX_VALUE
        else -> seconds * 1000L
    }

    data class SmsEquivalentState(val type: Int, val status: Int)

    /**
     * Reuse the existing SMS-oriented presentation state without manufacturing MMS delivery proof.
     * In particular MESSAGE_BOX_SENT maps to STATUS_NONE, never STATUS_COMPLETE.
     */
    fun mmsBoxToSmsEquivalent(messageBox: Int): SmsEquivalentState = when (messageBox) {
        Telephony.Mms.MESSAGE_BOX_INBOX -> SmsEquivalentState(
            Telephony.Sms.MESSAGE_TYPE_INBOX,
            Telephony.Sms.STATUS_NONE
        )
        Telephony.Mms.MESSAGE_BOX_SENT -> SmsEquivalentState(
            Telephony.Sms.MESSAGE_TYPE_SENT,
            Telephony.Sms.STATUS_NONE
        )
        Telephony.Mms.MESSAGE_BOX_DRAFTS -> SmsEquivalentState(
            Telephony.Sms.MESSAGE_TYPE_DRAFT,
            Telephony.Sms.STATUS_NONE
        )
        Telephony.Mms.MESSAGE_BOX_OUTBOX -> SmsEquivalentState(
            Telephony.Sms.MESSAGE_TYPE_OUTBOX,
            Telephony.Sms.STATUS_PENDING
        )
        Telephony.Mms.MESSAGE_BOX_FAILED -> SmsEquivalentState(
            Telephony.Sms.MESSAGE_TYPE_FAILED,
            Telephony.Sms.STATUS_FAILED
        )
        else -> SmsEquivalentState(0, Telephony.Sms.STATUS_NONE)
    }
}

class SmsConversationStore(private val context: Context) {

    data class Message(
        val id: Long,
        val address: String,
        val body: String,
        val timestampMs: Long,
        val type: Int,
        val threadId: Long,
        val status: Int = Telephony.Sms.STATUS_NONE
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
        val nowMs = System.currentTimeMillis()
        return (recentSmsMessages(bounded, nowMs) + recentMmsMessages(bounded, nowMs))
            .sortedWith(
                compareByDescending<Message> { SmsTimestampOrder.sortTimestamp(it.timestampMs, nowMs) }
                    .thenByDescending { it.id }
            )
            .take(bounded)
    }

    fun recentThreads(limit: Int = 50): List<ThreadSummary> {
        if (!canRead()) return emptyList()
        val bounded = limit.coerceIn(1, MAX_THREADS)
        val messages = recentMessages(MAX_MESSAGES)
        val nowMs = System.currentTimeMillis()
        return messages
            .groupBy { message -> message.threadId }
            .filterKeys { it > 0L }
            .map { (threadId, threadMessages) ->
                val latest = threadMessages.maxWith(
                    compareBy<Message>(
                        { SmsTimestampOrder.sortTimestamp(it.timestampMs, nowMs) },
                        { it.id }
                    )
                )
                ThreadSummary(
                    threadId = threadId,
                    address = latest.address,
                    latestBody = latest.body,
                    latestTimestampMs = latest.timestampMs,
                    messageCount = threadMessages.size
                )
            }
            .sortedWith(
                compareByDescending<ThreadSummary> {
                    SmsTimestampOrder.sortTimestamp(it.latestTimestampMs, nowMs)
                }.thenByDescending { it.threadId }
            )
            .take(bounded)
    }

    fun messagesForThread(threadId: Long, limit: Int = 100): List<Message> {
        if (!canRead() || threadId <= 0L) return emptyList()
        val bounded = limit.coerceIn(1, MAX_MESSAGES)
        val nowMs = System.currentTimeMillis()
        val sms = recentSmsMessages(bounded, nowMs, threadId)
        val mms = recentMmsMessages(bounded, nowMs, threadId)
        return (sms + mms)
            .sortedWith(
                compareBy<Message> { SmsTimestampOrder.sortTimestamp(it.timestampMs, nowMs) }
                    .thenBy { it.id }
            )
            .takeLast(bounded)
    }

    private fun recentSmsMessages(
        limit: Int,
        nowMs: Long = System.currentTimeMillis(),
        threadId: Long? = null
    ): List<Message> {
        val cutoff = SmsTimestampOrder.latestPlausibleTimestamp(nowMs).toString()
        fun load(datePredicate: String, queryLimit: Int): List<Message> = querySmsMessages(
            selection = if (threadId == null) datePredicate else
                "${Telephony.Sms.THREAD_ID}=? AND ($datePredicate)",
            selectionArgs = if (threadId == null) arrayOf("0", cutoff) else
                arrayOf(threadId.toString(), "0", cutoff),
            sortOrder = "${Telephony.Sms.DATE} DESC LIMIT $queryLimit"
        )
        return SmsTimestampOrder.loadWindow(limit,
            loadPlausible = { load("${Telephony.Sms.DATE}>=? AND ${Telephony.Sms.DATE}<=?", it) },
            loadAnomalous = { load("${Telephony.Sms.DATE}<? OR ${Telephony.Sms.DATE}>?", it) }
        )
    }

    private fun recentMmsMessages(
        limit: Int,
        nowMs: Long = System.currentTimeMillis(),
        threadId: Long? = null
    ): List<Message> {
        val cutoffSeconds = SmsTimestampOrder.latestPlausibleTimestamp(nowMs) / 1000L
        fun load(datePredicate: String, queryLimit: Int): List<Message> = queryMmsMessages(
            selection = if (threadId == null) datePredicate else
                "${Telephony.Mms.THREAD_ID}=? AND ($datePredicate)",
            selectionArgs = if (threadId == null) arrayOf("0", cutoffSeconds.toString()) else
                arrayOf(threadId.toString(), "0", cutoffSeconds.toString()),
            sortOrder = "${Telephony.Mms.DATE} DESC LIMIT $queryLimit"
        )
        return SmsTimestampOrder.loadWindow(limit,
            loadPlausible = { load("${Telephony.Mms.DATE}>=? AND ${Telephony.Mms.DATE}<=?", it) },
            loadAnomalous = { load("${Telephony.Mms.DATE}<? OR ${Telephony.Mms.DATE}>?", it) }
        )
    }

    private fun querySmsMessages(
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String
    ): List<Message> {
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.STATUS
        )
        return runCatching {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
                val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val typeIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                val threadIdIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
                val statusIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.STATUS)
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            Message(
                                id = cursor.getLong(idIndex),
                                address = cursor.getString(addressIndex).orEmpty().take(MAX_ADDRESS_CHARS),
                                body = cursor.getString(bodyIndex).orEmpty().take(SentinelSmsSender.MAX_BODY_CHARS),
                                timestampMs = cursor.getLong(dateIndex),
                                type = cursor.getInt(typeIndex),
                                threadId = cursor.getLong(threadIdIndex),
                                status = cursor.getInt(statusIndex)
                            )
                        )
                    }
                }
            } ?: emptyList()
        }.getOrDefault(emptyList())
    }

    private fun queryMmsMessages(
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String
    ): List<Message> {
        val projection = arrayOf(
            Telephony.Mms._ID,
            Telephony.Mms.THREAD_ID,
            Telephony.Mms.DATE,
            Telephony.Mms.MESSAGE_BOX
        )
        return runCatching {
            context.contentResolver.query(
                Telephony.Mms.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(Telephony.Mms._ID)
                val threadIndex = cursor.getColumnIndexOrThrow(Telephony.Mms.THREAD_ID)
                val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Mms.DATE)
                val boxIndex = cursor.getColumnIndexOrThrow(Telephony.Mms.MESSAGE_BOX)
                buildList {
                    while (cursor.moveToNext()) {
                        val providerId = cursor.getLong(idIndex).takeIf { it > 0L } ?: continue
                        val syntheticId = UnifiedProviderMessage.encodeMmsId(providerId) ?: continue
                        val messageBox = cursor.getInt(boxIndex)
                        val state = UnifiedProviderMessage.mmsBoxToSmsEquivalent(messageBox)
                        add(
                            Message(
                                id = syntheticId,
                                address = readMmsAddress(providerId, messageBox),
                                body = readMmsPreview(providerId),
                                timestampMs = UnifiedProviderMessage.mmsDateMs(cursor.getLong(dateIndex)),
                                type = state.type,
                                threadId = cursor.getLong(threadIndex),
                                status = state.status
                            )
                        )
                    }
                }
            } ?: emptyList()
        }.getOrDefault(emptyList())
    }

    private fun readMmsAddress(providerMessageId: Long, messageBox: Int): String {
        if (providerMessageId <= 0L) return ""
        val preferredType = if (messageBox == Telephony.Mms.MESSAGE_BOX_INBOX) {
            ADDRESS_TYPE_FROM
        } else {
            ADDRESS_TYPE_TO
        }
        return runCatching {
            context.contentResolver.query(
                mmsAddressUri(providerMessageId),
                arrayOf(Telephony.Mms.Addr.ADDRESS, Telephony.Mms.Addr.TYPE),
                null,
                null,
                null
            )?.use { cursor ->
                val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Mms.Addr.ADDRESS)
                val typeIndex = cursor.getColumnIndexOrThrow(Telephony.Mms.Addr.TYPE)
                var fallback = ""
                while (cursor.moveToNext()) {
                    val address = cursor.getString(addressIndex)
                        .orEmpty()
                        .trim()
                        .take(MAX_ADDRESS_CHARS)
                    if (address.isEmpty() || address.equals(INSERT_ADDRESS_TOKEN, ignoreCase = true)) continue
                    if (fallback.isEmpty()) fallback = address
                    if (cursor.getInt(typeIndex) == preferredType) return@use address
                }
                fallback
            }.orEmpty()
        }.getOrDefault("")
    }

    /** Read text metadata only. Binary part streams are deliberately not opened for list rendering. */
    private fun readMmsPreview(providerMessageId: Long): String {
        if (providerMessageId <= 0L) return MMS_PREVIEW_GENERIC
        return runCatching {
            context.contentResolver.query(
                mmsPartsUri(providerMessageId),
                arrayOf(
                    Telephony.Mms.Part.CONTENT_TYPE,
                    Telephony.Mms.Part.TEXT,
                    Telephony.Mms.Part.SEQ
                ),
                null,
                null,
                "${Telephony.Mms.Part.SEQ} ASC LIMIT $MAX_MMS_PARTS"
            )?.use { cursor ->
                val typeIndex = cursor.getColumnIndexOrThrow(Telephony.Mms.Part.CONTENT_TYPE)
                val textIndex = cursor.getColumnIndexOrThrow(Telephony.Mms.Part.TEXT)
                val textParts = mutableListOf<String>()
                var imageParts = 0
                var otherParts = 0
                var totalParts = 0
                while (cursor.moveToNext() && totalParts < MAX_MMS_PARTS) {
                    totalParts++
                    val mime = cursor.getString(typeIndex).orEmpty().lowercase()
                    when {
                        mime == "text/plain" -> {
                            val text = cursor.getString(textIndex)
                                .orEmpty()
                                .trim()
                                .take(MAX_MMS_PREVIEW_CHARS)
                            if (text.isNotEmpty() && textParts.joinToString("\n").length < MAX_MMS_PREVIEW_CHARS) {
                                textParts += text
                            }
                        }
                        mime.startsWith("image/") -> imageParts++
                        else -> otherParts++
                    }
                }
                val text = textParts.joinToString("\n").take(MAX_MMS_PREVIEW_CHARS)
                when {
                    text.isNotEmpty() -> text
                    imageParts > 0 -> "[MMS · $imageParts image(s)]"
                    otherParts > 0 || totalParts > 0 -> "[MMS · $totalParts partie(s)]"
                    else -> MMS_PREVIEW_GENERIC
                }
            } ?: MMS_PREVIEW_GENERIC
        }.getOrDefault(MMS_PREVIEW_GENERIC)
    }

    fun insertOutgoingOutbox(address: String, body: String, subscriptionId: Int): Long? {
        if (!holdsSmsRole()) return null
        val safeAddress = address.trim()
        if (
            safeAddress.isEmpty() ||
            safeAddress.length > MAX_ADDRESS_CHARS ||
            !safeAddress.all { it.isDigit() || it in "+*#" } ||
            body.isBlank() ||
            body.length > SentinelSmsSender.MAX_BODY_CHARS ||
            subscriptionId < 0
        ) return null

        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, safeAddress)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
            put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_PENDING)
            put(Telephony.Sms.SUBSCRIPTION_ID, subscriptionId)
        }
        return runCatching {
            context.contentResolver.insert(Telephony.Sms.Outbox.CONTENT_URI, values)
                ?.let(ContentUris::parseId)
                ?.takeIf { it > 0L }
        }.getOrNull()
    }

    fun markOutgoingSent(id: Long): Boolean {
        if (!holdsSmsRole() || id <= 0L) return false
        val uri = ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, id)
        val values = ContentValues().apply {
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
        }
        return runCatching {
            context.contentResolver.update(
                uri,
                values,
                "${Telephony.Sms.TYPE}!=?",
                arrayOf(Telephony.Sms.MESSAGE_TYPE_FAILED.toString())
            ) == 1
        }.getOrDefault(false)
    }

    fun markOutgoingFailed(id: Long): Boolean {
        if (!holdsSmsRole() || id <= 0L) return false
        val uri = ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, id)
        val values = ContentValues().apply {
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_FAILED)
            put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_FAILED)
        }
        return runCatching {
            context.contentResolver.update(uri, values, null, null) == 1
        }.getOrDefault(false)
    }

    fun markDeliveryResult(id: Long, successful: Boolean): Boolean {
        if (!holdsSmsRole() || id <= 0L) return false
        val uri = ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, id)
        val values = ContentValues().apply {
            put(
                Telephony.Sms.STATUS,
                if (successful) Telephony.Sms.STATUS_COMPLETE else Telephony.Sms.STATUS_FAILED
            )
        }
        return runCatching {
            if (successful) {
                context.contentResolver.update(
                    uri,
                    values,
                    "${Telephony.Sms.TYPE}!=?",
                    arrayOf(Telephony.Sms.MESSAGE_TYPE_FAILED.toString())
                ) == 1
            } else {
                context.contentResolver.update(uri, values, null, null) == 1
            }
        }.getOrDefault(false)
    }

    fun deleteMessage(id: Long): Boolean {
        if (!canRead() || id == 0L || id == Long.MIN_VALUE) return false
        val mmsProviderId = UnifiedProviderMessage.decodeMmsId(id)
        val uri = if (mmsProviderId != null) {
            ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, mmsProviderId)
        } else {
            if (id <= 0L) return false
            ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, id)
        }
        return runCatching {
            context.contentResolver.delete(uri, null, null) == 1
        }.getOrDefault(false)
    }

    /**
     * Deletes the complete Android conversation across both SMS and MMS only after explicit caller
     * confirmation. The platform's public MmsSms conversation URI is used as the single boundary so
     * Sentinel cannot leave a half-deleted SMS/MMS thread through two independent provider deletes.
     */
    fun deleteThread(threadId: Long): Int {
        if (!canRead() || threadId <= 0L) return 0
        val uri = ContentUris.withAppendedId(Telephony.MmsSms.CONTENT_CONVERSATIONS_URI, threadId)
        return runCatching {
            context.contentResolver.delete(uri, null, null).coerceAtLeast(0)
        }.getOrDefault(0)
    }

    /** Explicitly SMS-only until the export schema/disclosure is extended to MMS. */
    fun exportRecentMessages(limit: Int = 100): ExportResult? {
        if (!canRead()) return null
        val bounded = limit.coerceIn(1, MAX_MESSAGES)
        val messages = recentSmsMessages(bounded)
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
            .put("transport", "sms")
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

    private fun holdsSmsRole(): Boolean =
        context.readSmsRoleStateFailClosed() == SmsActivationDiagnostics.SmsRoleState.HELD

    private fun mmsAddressUri(providerMessageId: Long): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Telephony.Mms.Addr.getAddrUriForMessage(providerMessageId.toString())
        } else {
            Uri.parse("content://mms/$providerMessageId/addr")
        }

    private fun mmsPartsUri(providerMessageId: Long): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Telephony.Mms.Part.getPartUriForMessage(providerMessageId.toString())
        } else {
            Uri.parse("content://mms/$providerMessageId/part")
        }

    companion object {
        private const val MAX_MESSAGES = 200
        private const val MAX_THREADS = 50
        private const val MAX_ADDRESS_CHARS = 128
        private const val MAX_MMS_PARTS = 32
        private const val MAX_MMS_PREVIEW_CHARS = 1000
        private const val ADDRESS_TYPE_FROM = 0x89
        private const val ADDRESS_TYPE_TO = 0x97
        private const val INSERT_ADDRESS_TOKEN = "insert-address-token"
        private const val MMS_PREVIEW_GENERIC = "[MMS]"
        private const val EXPORT_DIRECTORY = "sentinel_sms_export"
        private const val EXPORT_MAX_AGE_MS = 24L * 60L * 60L * 1000L
    }
}
