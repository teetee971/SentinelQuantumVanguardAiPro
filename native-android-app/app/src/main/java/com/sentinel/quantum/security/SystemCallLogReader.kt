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
    enum class AccessState { READY, ROLE_OR_PERMISSION_REQUIRED, PROVIDER_UNAVAILABLE }

    data class Entry(
        val number: String?,
        val type: Int,
        val dateMillis: Long,
        val durationSeconds: Long,
        val cachedName: String? = null
    )

    fun canRead(): Boolean =
        holdsDialerRole() &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) ==
            PackageManager.PERMISSION_GRANTED

    fun accessState(): AccessState {
        if (!canRead()) return AccessState.ROLE_OR_PERMISSION_REQUIRED
        return try {
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls._ID),
                null,
                null,
                null
            ) ?: return AccessState.PROVIDER_UNAVAILABLE
            cursor.use { AccessState.READY }
        } catch (_: SecurityException) {
            AccessState.ROLE_OR_PERMISSION_REQUIRED
        } catch (_: RuntimeException) {
            AccessState.PROVIDER_UNAVAILABLE
        }
    }

    fun recent(limit: Int = 100): List<Entry> {
        if (!canRead()) return emptyList()
        val boundedLimit = limit.coerceIn(1, MAX_ROWS)
        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.CACHED_NAME
        )
        val result = ArrayList<Entry>(boundedLimit)
        return try {
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
                val cachedNameIndex = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                var scannedRows = 0
                while (
                    cursor.moveToNext() &&
                    result.size < boundedLimit &&
                    scannedRows < MAX_SCAN_ROWS
                ) {
                    scannedRows++
                    val entry = Entry(
                        number = if (numberIndex >= 0 && !cursor.isNull(numberIndex)) {
                            cursor.getString(numberIndex)?.take(MAX_NUMBER_CHARS)
                        } else null,
                        type = cursor.getInt(typeIndex),
                        dateMillis = cursor.getLong(dateIndex).coerceAtLeast(0L),
                        durationSeconds = cursor.getLong(durationIndex).coerceAtLeast(0L),
                        cachedName = if (
                            cachedNameIndex >= 0 &&
                            !cursor.isNull(cachedNameIndex)
                        ) {
                            cursor.getString(cachedNameIndex)
                                ?.trim()
                                ?.take(MAX_CACHED_NAME_CHARS)
                                ?.takeIf { it.isNotBlank() }
                        } else null
                    )
                    val previous = result.lastOrNull()
                    val duplicate = previous != null && CallLogDeduplicationPolicy.sameVisibleCall(
                        previous.number,
                        previous.type,
                        previous.dateMillis,
                        previous.durationSeconds,
                        entry.number,
                        entry.type,
                        entry.dateMillis,
                        entry.durationSeconds
                    )
                    if (!duplicate) result += entry
                }
            }
            result
        } catch (_: SecurityException) {
            emptyList()
        } catch (_: RuntimeException) {
            emptyList()
        }
    }

    private fun holdsDialerRole(): Boolean =
        AndroidRoleReadPolicy.readBoolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(RoleManager::class.java)
                roleManager.isRoleAvailable(RoleManager.ROLE_DIALER) &&
                    roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
            } else {
                context.getSystemService(TelecomManager::class.java).defaultDialerPackage ==
                    context.packageName
            }
        }

    private companion object {
        const val MAX_ROWS = 500
        const val MAX_SCAN_ROWS = 1_500
        const val MAX_NUMBER_CHARS = 64
        const val MAX_CACHED_NAME_CHARS = 160
    }
}
