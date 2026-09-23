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
import androidx.core.content.ContextCompat
import com.sentinel.quantum.R
import com.sentinel.quantum.SmsComposeActivity
import com.sentinel.quantum.data.SettingsStore

/**
 * Local notification helper for the staged default-SMS client.
 * Message content is not uploaded; Android 13+ notification permission is respected.
 */
object SmsNotificationHelper {
    private const val CHANNEL_ID = "sentinel_sms"
    private const val CHANNEL_NAME = "Messages Sentinel"

    fun notifyMessage(
        context: Context,
        title: String,
        preview: String,
        notificationId: Int
    ) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        val manager = context.getSystemService(NotificationManager::class.java)
        ensureChannel(context)
        if (!isChannelEnabled(context)) return

        val presentation = SmsNotificationPrivacy.presentation(
            previewEnabled = SettingsStore(context).smsNotificationPreviewEnabled,
            sender = title,
            message = preview
        )

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, SmsComposeActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(presentation.title)
            .setContentText(presentation.text)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(open)
        presentation.expandedText?.let {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(it))
        }
        manager.notify(notificationId, builder.build())
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications locales des messages Sentinel"
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
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
