package com.sentinel.quantum.background

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object CollectiveDefenseWorkScheduler {
    const val WORK_NAME = "sentinel_collective_defense_watch"

    fun schedule(context: Context, intervalHours: Int) {
        val sanitized = CollectiveDefensePreferences.sanitizeInterval(intervalHours)
        if (sanitized == CollectiveDefensePreferences.INTERVAL_NEVER) {
            cancel(context)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<CollectiveDefenseWatchWorker>(
            sanitized.toLong(),
            TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .addTag(WORK_NAME)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    fun sync(context: Context) {
        schedule(context, CollectiveDefensePreferences(context).refreshIntervalHours)
    }
}
