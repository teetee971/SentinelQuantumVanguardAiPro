package com.sentinel.quantum.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sentinel.quantum.data.OsintFeedCache
import com.sentinel.quantum.data.OsintRepository
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
        val context = applicationContext
        val cache = OsintFeedCache(context)
        val repository = OsintRepository(cache)

        val cached = cache.load()?.items.orEmpty()
        val fresh = try {
            repository.fetchAllFeeds()
        } catch (_: Exception) {
            emptyList()
        }

        if (fresh.isEmpty()) {
            // No network or unreachable sources: retry later, keep the cache untouched.
            return if (runAttemptCount < MAX_RETRY_ATTEMPTS) Result.retry() else Result.success()
        }

        val newItems = OsintFeedDiff.newItems(cached, fresh)

        // Always persist the refreshed snapshot (read markings are stored separately in OsintFeedCache).
        cache.save(OsintFeedDiff.merge(cached, fresh))

        if (newItems.isNotEmpty() && SettingsStore(context).osintNotificationsEnabled) {
            OsintNotificationHelper.notifyNewAlerts(
                context = context,
                newCount = newItems.size,
                latestTitle = newItems.first().title
            )
        }

        // Silent run when nothing new is detected.
        return Result.success()

    private companion object {
        const val MAX_RETRY_ATTEMPTS = 3
    }
}
