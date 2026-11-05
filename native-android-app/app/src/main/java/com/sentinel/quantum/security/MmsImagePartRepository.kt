package com.sentinel.quantum.security

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.net.Uri
import android.provider.Telephony

internal class MmsImagePartRepository(
    context: Context
) {
    private val appContext = context.applicationContext
    private val conversations = SmsConversationStore(appContext)

    fun findImages(
        syntheticMessageId: Long,
        threadId: Long
    ): List<MmsThumbnailPolicy.PartKey> {
        val identity = MmsImagePartContract.resolveMessage(
            syntheticMessageId,
            threadId
        ) ?: return emptyList()

        if (!conversations.canRead()) return emptyList()
        if (!parentMatchesThread(identity)) return emptyList()
        if (!conversations.canRead()) return emptyList()

        val results = mutableListOf<MmsThumbnailPolicy.PartKey>()

        try {
            appContext.contentResolver.query(
                partsUri(identity.providerMessageId),
                arrayOf(
                    "_id",
                    "mid",
                    Telephony.Mms.Part.CONTENT_TYPE
                ),
                null,
                null,
                null
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow("_id")
                val parentColumn = cursor.getColumnIndexOrThrow("mid")
                val mimeColumn = cursor.getColumnIndexOrThrow(
                    Telephony.Mms.Part.CONTENT_TYPE
                )

                var examined = 0

                while (cursor.moveToNext()) {
                    examined++
                    if (examined > 32) return emptyList()

                    if (!conversations.canRead()) {
                        return emptyList()
                    }

                    val part = MmsImagePartContract.PartMetadata(
                        partId = cursor.getLong(idColumn),
                        parentMessageId = cursor.getLong(parentColumn),
                        mimeType = cursor.getString(mimeColumn)
                    )

                    MmsImagePartContract.acceptPart(
                        identity,
                        part
                    )?.let(results::add)
                }
            } ?: return emptyList()
        } catch (_: SecurityException) {
            return emptyList()
        } catch (_: RuntimeException) {
            return emptyList()
        }

        if (!conversations.canRead()) return emptyList()
        if (!parentMatchesThread(identity)) return emptyList()

        return results
    }

    private fun parentMatchesThread(
        identity: MmsImagePartContract.MessageIdentity
    ): Boolean {
        if (!conversations.canRead()) return false

        return try {
            val uri = ContentUris.withAppendedId(
                Telephony.Mms.CONTENT_URI,
                identity.providerMessageId
            )

            appContext.contentResolver.query(
                uri,
                arrayOf(
                    Telephony.Mms._ID,
                    Telephony.Mms.THREAD_ID
                ),
                null,
                null,
                null
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(
                    Telephony.Mms._ID
                )
                val threadColumn = cursor.getColumnIndexOrThrow(
                    Telephony.Mms.THREAD_ID
                )

                if (!cursor.moveToFirst()) {
                    false
                } else {
                    val matches =
                        cursor.getLong(idColumn) ==
                            identity.providerMessageId &&
                        cursor.getLong(threadColumn) ==
                            identity.threadId
                    matches && !cursor.moveToNext()
                }
            } == true
        } catch (_: SecurityException) {
            false
        } catch (_: RuntimeException) {
            false
        }
    }

    private fun partsUri(providerMessageId: Long): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Telephony.Mms.Part.getPartUriForMessage(
                providerMessageId.toString()
            )
        } else {
            Uri.parse(
                "content://mms/$providerMessageId/part"
            )
        }
}
