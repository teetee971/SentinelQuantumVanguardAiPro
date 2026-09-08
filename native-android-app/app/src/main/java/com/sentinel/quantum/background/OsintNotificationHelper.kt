package com.sentinel.quantum.background

import android.app.NotificationChannel
import android.app.NotificationManager
import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sentinel.quantum.MainActivity
import com.sentinel.quantum.R

/**
 * Local notification surface for the OSINT background watch. No remote push service is used:
 * notifications are posted by [OsintRefreshWorker] from data already fetched on device.
 */
internal object OsintNotificationHelper {

    const val CHANNEL_ID = "osint_alerts"
    const val EXTRA_OPEN_OSINT_FEED = "com.sentinel.quantum.OPEN_OSINT_FEED"

    private const val NOTIFICATION_ID = 4201
    private const val REQUEST_CODE = 4201
    private const val MAX_TEXT_CHARS = 200

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.osint_notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.osint_notification_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Posts a single notification summarising [newCount] new alerts. Returns false when the user
     * (or the system) has not granted notifications, in which case nothing is posted. No runtime
     * permission is requested by this app.
     */
    fun notifyNewAlerts(context: Context, newCount: Int, latestTitle: String): Boolean {
        if (newCount <= 0) return false
        val manager = NotificationManagerCompat.from(context)
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        if (!manager.areNotificationsEnabled()) return false

        ensureChannel(context)

        val title = if (newCount == 1) {
            context.getString(R.string.osint_notification_title_single)
        } else {
            context.getString(R.string.osint_notification_title_multiple, newCount)
        }
        val text = latestTitle.take(MAX_TEXT_CHARS)

        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_OSINT_FEED, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        return try {
            manager.notify(NOTIFICATION_ID, notification)
            true
        } catch (_: SecurityException) {
            // Defensive: notifications can be revoked between the check and the post.
            false
        }
    }
}
