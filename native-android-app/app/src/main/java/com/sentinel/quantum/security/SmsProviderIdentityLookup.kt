package com.sentinel.quantum.security

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat

/**
 * Minimal identity-only lookup for an already validated SMS provider row.
 *
 * This boundary deliberately projects ADDRESS only. Message body, timestamps and other provider
 * metadata never cross into the Intelligence Core wiring path. Callers must immediately convert
 * the returned address into the existing device-bound fingerprint and must not persist/log it.
 */
class SmsProviderIdentityLookup(private val context: Context) {
    fun addressForProviderMessage(providerMessageId: Long): String? {
        if (providerMessageId <= 0L || !canReadProvider()) return null

        return runCatching {
            context.contentResolver.query(
                ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, providerMessageId),
                arrayOf(Telephony.Sms.ADDRESS),
                null,
                null,
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS))
                    ?.trim()
                    ?.take(MAX_ADDRESS_CHARS)
                    ?.takeIf { it.isNotBlank() }
            }
        }.getOrNull()
    }

    private fun canReadProvider(): Boolean =
        Telephony.Sms.getDefaultSmsPackage(context) == context.packageName &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        const val MAX_ADDRESS_CHARS = 128
    }
}
