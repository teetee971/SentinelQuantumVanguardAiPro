package com.sentinel.quantum.background

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Queues the published ARCEP/RTR refresh. Never used from the call-screening callback. */
object PublishedNumberCatalogScheduler {
    const val PERIODIC_WORK = "sentinel_published_number_catalog"
    const val IMMEDIATE_WORK = "sentinel_published_number_catalog_now"

    fun sync(context: Context, enabled: Boolean) {
        val app = context.applicationContext
        val manager = WorkManager.getInstance(app)
        if (!enabled) {
            manager.cancelUniqueWork(PERIODIC_WORK)
            manager.cancelUniqueWork(IMMEDIATE_WORK)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val periodic = PeriodicWorkRequestBuilder<PublishedNumberCatalogWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .addTag(PERIODIC_WORK)
            .build()
        manager.enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, periodic)
        val now = OneTimeWorkRequestBuilder<PublishedNumberCatalogWorker>()
            .setConstraints(constraints)
            .addTag(IMMEDIATE_WORK)
            .build()
        manager.enqueueUniqueWork(IMMEDIATE_WORK, ExistingWorkPolicy.KEEP, now)
    }
}
