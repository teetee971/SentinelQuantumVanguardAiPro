package com.sentinel.quantum.security

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import com.sentinel.quantum.R
import com.sentinel.quantum.SentinelInCallActivity

/**
 * Incoming-call notification surface for the user-selected default dialer.
 *
 * Telecom keeps responsibility for the ringtone because Sentinel does not declare
 * IN_CALL_SERVICE_RINGING. This helper only provides the heads-up/full-screen UI path.
 */
object SentinelCallNotificationHelper {
    const val ACTION_ANSWER = "com.sentinel.quantum.CALL_ANSWER"
    const val ACTION_REJECT = "com.sentinel.quantum.CALL_REJECT"
    private const val CHANNEL_ID = "sentinel_incoming_calls"
    private const val NOTIFICATION_ID = 5101

    fun showIncoming(
        context: Context,
        snapshot: SentinelInCallService.CallSnapshot
    ): Boolean {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        ) return false

        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        ensureChannel(context)

        val label = snapshot.displayName?.takeIf { it.isNotBlank() }
            ?: snapshot.handle?.takeIf { it.isNotBlank() }
            ?: "Appel entrant"

        val fullScreen = PendingIntent.getActivity(
            context,
            0,
            Intent(context, SentinelInCallActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val answer = PendingIntent.getBroadcast(
            context,
            1,
            Intent(ACTION_ANSWER).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val reject = PendingIntent.getBroadcast(
            context,
            2,
            Intent(ACTION_REJECT).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val caller = Person.Builder().setName(label).build()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, true)
            .setStyle(NotificationCompat.CallStyle.forIncomingCall(caller, reject, answer))
            .build()

        return runCatching {
            manager.notify(NOTIFICATION_ID, notification)
            true
        }.getOrDefault(false)
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val system = context.getSystemService(NotificationManager::class.java)
        if (system.getNotificationChannel(CHANNEL_ID) != null) return
        system.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Appels entrants Sentinel",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Interface d’appel entrant du composeur Sentinel"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    fun isChannelEnabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        ensureChannel(context)
        val channel = context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(CHANNEL_ID)
        return channel != null && channel.importance != NotificationManager.IMPORTANCE_NONE
    }
}
