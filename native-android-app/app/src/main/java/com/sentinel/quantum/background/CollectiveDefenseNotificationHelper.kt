package com.sentinel.quantum.background

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
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

internal object CollectiveDefenseNotificationHelper {
    const val CHANNEL_ID = "collective_defense_alerts"
    const val EXTRA_OPEN_COLLECTIVE_DEFENSE =
        "com.sentinel.quantum.OPEN_COLLECTIVE_DEFENSE"

    private const val NOTIFICATION_ID = 4301
    private const val REQUEST_CODE = 4301

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.collective_defense_notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(
                    R.string.collective_defense_notification_channel_description
                )
            }
        )
    }

    fun notifyRiskIncrease(
        context: Context,
        count: Int,
        highestRiskState: String
    ): Boolean {
        if (count <= 0) return false
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return false

        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        ensureChannel(context)

        val title = if (count == 1) {
            context.getString(R.string.collective_defense_notification_title_single)
        } else {
            context.getString(R.string.collective_defense_notification_title_multiple, count)
        }
        val text = when (highestRiskState) {
            "HIGH_CONFIDENCE" ->
                "Un indicateur surveillé dispose maintenant de signaux fortement corroborés."
            "SUSPICIOUS" ->
                "Un indicateur surveillé présente maintenant plusieurs signaux suspects."
            else ->
                "La réputation d’un indicateur surveillé a évolué."
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_COLLECTIVE_DEFENSE, true)
        }
        val pending = PendingIntent.getActivity(
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
            .setContentIntent(pending)
            .build()

        return try {
            manager.notify(NOTIFICATION_ID, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }
}
