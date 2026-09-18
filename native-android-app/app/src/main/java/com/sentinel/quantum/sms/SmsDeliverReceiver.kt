package com.sentinel.quantum.sms

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sentinel.quantum.R

class SmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val repository = SmsRepository(context)
        if (!repository.isDefaultHandler()) return

        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (parts.isEmpty()) return
        val sender = parts.firstOrNull()?.originatingAddress
        val body = parts.joinToString(separator = "") { it.messageBody.orEmpty() }.take(10_000)
        val timestamp = parts.minOfOrNull { it.timestampMillis } ?: System.currentTimeMillis()

        if (repository.storeIncoming(sender, body, timestamp)) {
            notifyIncoming(context)
        }
    }

    private fun notifyIncoming(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Messages SMS",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Nouveaux SMS reçus lorsque Sentinel est l’application SMS par défaut"
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
                }
            )
        }

        NotificationManagerCompat.from(context).notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Nouveau SMS reçu")
                .setContentText("Ouvrez Sentinel pour consulter le message.")
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .build()
        )
    }

    private companion object {
        const val CHANNEL_ID = "sentinel_sms"
        const val NOTIFICATION_ID = 4101
    }
}
