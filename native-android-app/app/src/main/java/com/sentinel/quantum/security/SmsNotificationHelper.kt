package com.sentinel.quantum.security

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.PermissionChecker
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
    ): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= 33 &&
            PermissionChecker.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PermissionChecker.PERMISSION_GRANTED
        ) return@runCatching false

        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return@runCatching false
        val manager = context.getSystemService(NotificationManager::class.java)
            ?: return@runCatching false
        if (!ensureChannel(context)) return@runCatching false
        if (!isChannelEnabled(context)) return@runCatching false

        val presentation = SmsNotificationPrivacy.presentation(
            previewEnabled = SettingsStore(context).smsNotificationPreviewEnabled,
            sender = title,
            message = preview
        )

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, SmsComposeActivity::class.java)
                .putExtra(SmsComposeActivity.EXTRA_OPEN_CONVERSATIONS, true),
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
        true
    }.getOrDefault(false)

    fun ensureChannel(context: Context): Boolean = runCatching {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return@runCatching true
        val manager = context.getSystemService(NotificationManager::class.java)
            ?: return@runCatching false
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return@runCatching true
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
        true
    }.getOrDefault(false)

    fun isChannelEnabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        if (!ensureChannel(context)) return false
        val channel = context.getSystemService(NotificationManager::class.java)
            ?.getNotificationChannel(CHANNEL_ID)
        return channel != null && channel.importance != NotificationManager.IMPORTANCE_NONE
    }
}
