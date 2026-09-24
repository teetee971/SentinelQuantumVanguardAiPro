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

        val count = intent.getIntExtra(TelecomManager.EXTRA_NOTIFICATION_COUNT, 0)
            .coerceAtLeast(0)
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

        ensureChannel(context)
        val openDialer = PendingIntent.getActivity(
            context,
            0,
            Intent(context, SentinelDialerActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = if (count == 1) "Appel manqué" else "$count appels manqués"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText("Ouvrir Sentinel pour consulter les appels récents.")
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openDialer)
            .setNumber(count)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun holdsDialerRole(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roles = context.getSystemService(RoleManager::class.java)
            roles.isRoleAvailable(RoleManager.ROLE_DIALER) &&
                roles.isRoleHeld(RoleManager.ROLE_DIALER)
        } else {
            context.getSystemService(TelecomManager::class.java).defaultDialerPackage ==
                context.packageName
        }

    private fun ensureChannel(context: Context) {
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

    private companion object {
        const val CHANNEL_ID = "sentinel_missed_calls"
        const val NOTIFICATION_ID = 5102
    }
}
