package com.sentinel.quantum.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sentinel.quantum.BuildConfig
import com.sentinel.quantum.data.OsintFeedCache
import com.sentinel.quantum.data.OsintRepository
import com.sentinel.quantum.data.OsintRssConfig
import com.sentinel.quantum.data.SettingsStore

/**
 * Periodic, local-only OSINT watch. Fetches the public feeds already used by the app, compares
 * them with the local cache and posts a notification when new alerts appear. No backend, no
 * tracking: everything stays on the device.
 */
class OsintRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (BuildConfig.COMMERCIAL_PHONE_CORE_V1) return Result.success()
        val context = applicationContext
        val cache = OsintFeedCache(context)
        val repository = OsintRepository()

        val cachedSnapshot = cache.load()
        val allowedSources = OsintRssConfig.fetchSources(SettingsStore(context).osintRssSourceNames())
        val allowedNames = allowedSources.map { it.displayName }.toSet()
        val fetchResult = try {
            repository.fetchAllFeedsResult(allowedSources)
        } catch (_: Exception) {
            null
        }

        if (fetchResult == null || !fetchResult.isComplete || fetchResult.items.isEmpty()) {
            // Never replace a complete cache with a partial or empty refresh.
            return if (runAttemptCount < MAX_RETRY_ATTEMPTS) Result.retry() else Result.success()
        }

        val fresh = OsintFeedDiff.retainSources(fetchResult.items, allowedNames)
        val cached = OsintFeedDiff.notificationBaseline(cachedSnapshot?.items, allowedNames)
        val newItems = OsintFeedDiff.newItemsForNotification(cached, fresh)

        // Disabled feeds must not be merged back in from the previous snapshot.
        cache.save(if (cached == null) fresh else OsintFeedDiff.merge(cached, fresh))

        if (newItems.isNotEmpty() && SettingsStore(context).osintNotificationsEnabled) {
            OsintNotificationHelper.notifyNewAlerts(
                context = context,
                newCount = newItems.size,
                latestTitle = newItems.first().title
            )
        }

        return Result.success()
    }

    private companion object {
        const val MAX_RETRY_ATTEMPTS = 3
    }
}
