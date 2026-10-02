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
        val durationSeconds: Long
    )

    data class RecentResult(
        val state: AccessState,
        val entries: List<Entry>
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

    fun recentWithState(limit: Int = 100): RecentResult {
        if (!canRead()) return RecentResult(AccessState.ROLE_OR_PERMISSION_REQUIRED, emptyList())
        val boundedLimit = limit.coerceIn(1, MAX_ROWS)
        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION
        )
        val result = ArrayList<Entry>(boundedLimit)
        return try {
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            ) ?: return RecentResult(AccessState.PROVIDER_UNAVAILABLE, emptyList())
            cursor.use {
                val numberIndex = it.getColumnIndex(CallLog.Calls.NUMBER)
                val typeIndex = it.getColumnIndexOrThrow(CallLog.Calls.TYPE)
                val dateIndex = it.getColumnIndexOrThrow(CallLog.Calls.DATE)
                val durationIndex = it.getColumnIndexOrThrow(CallLog.Calls.DURATION)
                while (it.moveToNext() && result.size < boundedLimit) {
                    result += Entry(
                        number = if (numberIndex >= 0 && !it.isNull(numberIndex)) {
                            it.getString(numberIndex)?.take(MAX_NUMBER_CHARS)
                        } else null,
                        type = it.getInt(typeIndex),
                        dateMillis = it.getLong(dateIndex).coerceAtLeast(0L),
                        durationSeconds = it.getLong(durationIndex).coerceAtLeast(0L)
                    )
                }
            }
            RecentResult(AccessState.READY, result)
        } catch (_: SecurityException) {
            RecentResult(AccessState.ROLE_OR_PERMISSION_REQUIRED, emptyList())
        } catch (_: RuntimeException) {
            RecentResult(AccessState.PROVIDER_UNAVAILABLE, emptyList())
        }
    }

    fun recent(limit: Int = 100): List<Entry> = recentWithState(limit).entries

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
        const val MAX_NUMBER_CHARS = 64
    }
}
