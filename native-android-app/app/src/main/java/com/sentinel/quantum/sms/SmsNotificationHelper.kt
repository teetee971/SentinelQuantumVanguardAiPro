package com.sentinel.quantum.sms

import android.Manifest
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

object SmsNotificationHelper {
    private const val CHANNEL_ID = "sentinel_sms"
    private const val CHANNEL_NAME = "Messages Sentinel"

    fun notifyMessage(context: Context, title: String, preview: String, notificationId: Int) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, SmsComposeActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        manager.notify(
            notificationId,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title.take(120))
                .setContentText(preview.take(180))
                .setStyle(NotificationCompat.BigTextStyle().bigText(preview.take(1000)))
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
        )
    }
}
