package com.sentinel.quantum.security

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.CallLog
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat

/**
 * Fail-closed read boundary for Android's system call log.
 *
 * Call-log access is allowed only while Sentinel is the user-selected default dialer and the
 * dedicated runtime permission is granted. No row is uploaded or persisted by this component.
 */
class SystemCallLogReader(private val context: Context) {
    data class Entry(
        val number: String?,
        val type: Int,
        val dateMillis: Long,
        val durationSeconds: Long
    )

    fun canRead(): Boolean =
        holdsDialerRole() &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) ==
            PackageManager.PERMISSION_GRANTED

    fun recent(limit: Int = 100): List<Entry> {
        if (!canRead()) return emptyList()
        val boundedLimit = limit.coerceIn(1, MAX_ROWS)
        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION
        )
        val result = ArrayList<Entry>(boundedLimit)
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            projection,
            null,
            null,
            "${CallLog.Calls.DATE} DESC"
        )?.use { cursor ->
            val numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER)
            val typeIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
            val dateIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
            val durationIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)
            while (cursor.moveToNext() && result.size < boundedLimit) {
                result += Entry(
                    number = if (numberIndex >= 0 && !cursor.isNull(numberIndex)) {
                        cursor.getString(numberIndex)?.take(MAX_NUMBER_CHARS)
                    } else null,
                    type = cursor.getInt(typeIndex),
                    dateMillis = cursor.getLong(dateIndex).coerceAtLeast(0L),
                    durationSeconds = cursor.getLong(durationIndex).coerceAtLeast(0L)
                )
            }
        }
        return result
    }

    private fun holdsDialerRole(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            roleManager.isRoleAvailable(RoleManager.ROLE_DIALER) &&
                roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
        } else {
            context.getSystemService(TelecomManager::class.java).defaultDialerPackage ==
                context.packageName
        }

    private companion object {
        const val MAX_ROWS = 500
        const val MAX_NUMBER_CHARS = 64
    }
}
