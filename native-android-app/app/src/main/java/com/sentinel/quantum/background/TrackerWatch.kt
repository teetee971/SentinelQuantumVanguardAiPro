package com.sentinel.quantum.background

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.sentinel.quantum.R
import com.sentinel.quantum.security.BluetoothScanner
import com.sentinel.quantum.security.NetworkTrustStore
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class TrackerWatchPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = preferences.getBoolean(KEY_ENABLED, false)
        set(value) {
            preferences.edit().putBoolean(KEY_ENABLED, value).apply()
        }

    fun alreadyNotified(identity: String, day: String): Boolean =
        preferences.getStringSet(KEY_NOTIFIED, emptySet())?.contains("$day:$identity") == true

    fun rememberNotified(identity: String, day: String) {
        val next = preferences.getStringSet(KEY_NOTIFIED, emptySet()).orEmpty().toMutableSet()
        next += "$day:$identity"
        while (next.size > 40) next.remove(next.first())
        preferences.edit().putStringSet(KEY_NOTIFIED, next).apply()
    }

    private companion object {
        const val PREFS = "tracker_watch"
        const val KEY_ENABLED = "enabled"
        const val KEY_NOTIFIED = "notified"
    }
}

object TrackerWatchScheduler {
    const val WORK_NAME = "sentinel_tracker_watch"

    fun sync(context: Context, enabled: Boolean) {
        val manager = WorkManager.getInstance(context.applicationContext)
        if (!enabled) {
            manager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<TrackerWatchWorker>(15, TimeUnit.MINUTES).build()
        manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}

class TrackerWatchWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val preferences = TrackerWatchPreferences(applicationContext)
        if (!preferences.enabled) return Result.success()
        val scanner = BluetoothScanner(applicationContext)
        if (!scanner.hasPermissions()) {
            scanner.close()
            return Result.success()
        }
        val latch = CountDownLatch(1)
        val found = AtomicReference(emptyList<com.sentinel.quantum.security.DiscoveredBluetoothDevice>())
        scanner.scan(
            durationMs = 4_000,
            onResults = { found.set(it) },
            onError = { latch.countDown() },
            onScanFinished = { latch.countDown() }
        )
        latch.await(8, TimeUnit.SECONDS)
        scanner.close()
        val trust = NetworkTrustStore(applicationContext)
        val day = (System.currentTimeMillis() / 86_400_000L).toString()
        val moved = found.get().filter { device ->
            val identity = device.alertKey ?: device.address
            val interesting = device.assessment.announcesFindMy ||
                device.assessment.announcesKnownTracker ||
                device.assessment.likelyTracker
            interesting &&
                device.assessment.reasons.any { it.contains("réseaux différents") } &&
                !trust.isAllowed("tracker:$identity") &&
                !preferences.alreadyNotified(identity, day)
        }
        if (moved.isEmpty()) return Result.success()
        notify(moved.size)
        moved.forEach { preferences.rememberNotified(it.alertKey ?: it.address, day) }
        return Result.success()
    }

    private fun notify(count: Int) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(applicationContext, android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = applicationContext.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Traceurs", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val text = if (count == 1) {
            "Un objet déjà vu est maintenant sur un autre réseau. Ce n’est pas une preuve de suivi."
        } else {
            "$count objets déjà vus sont maintenant sur un autre réseau. Ce n’est pas une preuve de suivi."
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Sentinel")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private companion object {
        const val CHANNEL = "tracker_watch"
        const val NOTIFICATION_ID = 48120
    }
}
