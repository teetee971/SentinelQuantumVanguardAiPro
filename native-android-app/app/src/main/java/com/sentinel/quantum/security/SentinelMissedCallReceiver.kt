package com.sentinel.quantum.security

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.TelecomManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sentinel.quantum.R
import com.sentinel.quantum.SentinelDialerActivity

/**
 * Handles Telecom's missed-call notification contract for the user-selected default dialer.
 * The broadcast carries only aggregate notification state here; Sentinel does not infer or
 * transmit caller identity.
 */
class SentinelMissedCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelecomManager.ACTION_SHOW_MISSED_CALLS_NOTIFICATION) return
        if (!holdsDialerRole(context)) return

        // Treat the external broadcast as untrusted input even though the manifest requires the
        // signature-only MODIFY_PHONE_STATE permission from its sender. We consume only a bounded
        // aggregate count and deliberately ignore caller-number/account extras.
        val count = intent.getIntExtra(TelecomManager.EXTRA_NOTIFICATION_COUNT, 0)
            .coerceIn(0, MAX_MISSED_CALL_COUNT)
        val manager = NotificationManagerCompat.from(context)
        if (count == 0) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        ) return
        if (!manager.areNotificationsEnabled()) return
        if (!isChannelEnabled(context)) return

        val openDialer = PendingIntent.getActivity(
            context,
            0,
            Intent(context, SentinelDialerActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val clearMissedCalls = readClearMissedCallsIntent(intent)
        val title = if (count == 1) "Appel manqué" else "$count appels manqués"
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText("Ouvrir Sentinel Téléphone.")
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openDialer)
            .setNumber(count)
        clearMissedCalls?.let(builder::setDeleteIntent)
        manager.notify(NOTIFICATION_ID, builder.build())
    }

    private fun holdsDialerRole(context: Context): Boolean =
        AndroidRoleReadPolicy.readBoolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roles = context.getSystemService(RoleManager::class.java)
                roles.isRoleAvailable(RoleManager.ROLE_DIALER) &&
                    roles.isRoleHeld(RoleManager.ROLE_DIALER)
            } else {
                context.getSystemService(TelecomManager::class.java).defaultDialerPackage ==
                    context.packageName
            }
        }

    @Suppress("DEPRECATION")
    private fun readClearMissedCallsIntent(intent: Intent): PendingIntent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_CLEAR_MISSED_CALLS_INTENT, PendingIntent::class.java)
        } else {
            intent.getParcelableExtra(EXTRA_CLEAR_MISSED_CALLS_INTENT)
        }

    companion object {
        private const val CHANNEL_ID = "sentinel_missed_calls"
        private const val NOTIFICATION_ID = 5102
        private const val MAX_MISSED_CALL_COUNT = 99

        // System API on some Android SDK surfaces; keep the wire key literal for API 24+ support.
        private const val EXTRA_CLEAR_MISSED_CALLS_INTENT =
            "android.telecom.extra.CLEAR_MISSED_CALLS_INTENT"

        /** Ensure the default-dialer missed-call channel exists without overriding user choices. */
        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val system = context.getSystemService(NotificationManager::class.java)
            if (system.getNotificationChannel(CHANNEL_ID) != null) return
            system.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Appels manqués Sentinel",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications d'appels manqués du composeur Sentinel"
                }
            )
        }

        /** Channel truth only; global notification permission/state is evaluated separately. */
        fun isChannelEnabled(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
            ensureChannel(context)
            val channel = context.getSystemService(NotificationManager::class.java)
                .getNotificationChannel(CHANNEL_ID) ?: return false
            return channel.importance != NotificationManager.IMPORTANCE_NONE
        }
    }
}
