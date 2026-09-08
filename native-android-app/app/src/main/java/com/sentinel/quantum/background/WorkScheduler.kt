package com.sentinel.quantum.background

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.sentinel.quantum.data.SettingsStore
import java.util.concurrent.TimeUnit

/**
 * Schedules the periodic [OsintRefreshWorker]. A single unique periodic work name is used so a
 * frequency change never stacks duplicated workers.
 */
object WorkScheduler {

    const val OSINT_WORK_NAME = "sentinel_osint_refresh"

    /**
     * Schedules the watch at [intervalHours], one of [SettingsStore.SUPPORTED_INTERVALS_HOURS].
     * [SettingsStore.INTERVAL_NEVER] (or any unsupported value) cancels the watch instead.
     */
    fun schedule(context: Context, intervalHours: Int) {
        val sanitized = SettingsStore.sanitizeInterval(intervalHours)
        if (sanitized == SettingsStore.INTERVAL_NEVER) {
            cancel(context)
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<OsintRefreshWorker>(
            sanitized.toLong(),
            TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .addTag(OSINT_WORK_NAME)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            OSINT_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(OSINT_WORK_NAME)
    }

    /** Applies the stored user preference; safe to call on every app start. */
    fun sync(context: Context) {
        schedule(context, SettingsStore(context).osintRefreshIntervalHours)
    }
}
