package com.sentinel.quantum.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sentinel.quantum.security.CollectiveDefenseClient
import com.sentinel.quantum.security.CollectiveDefenseWatchStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CollectiveDefenseWatchWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val context = applicationContext
        val store = CollectiveDefenseWatchStore(context)
        val watches = store.snapshot()
            .sortedBy { it.lastAttemptedAtMs }
            .take(MAX_RECHECKS_PER_RUN)
        if (watches.isEmpty()) return@withContext Result.success()

        val client = CollectiveDefenseClient()
        var failures = 0
        val escalated = mutableListOf<CollectiveDefenseClient.ReputationResult>()

        fun markAttemptedOrCountFailure(previous: CollectiveDefenseWatchStore.WatchItem) {
            if (!store.markAttempted(previous.indicatorType, previous.fingerprint)) {
                failures++
            }
        }

        watches.forEach { previous ->
            val refreshed = runCatching {
                client.lookupFingerprint(previous.indicatorType, previous.fingerprint)
            }.getOrElse {
                failures++
                markAttemptedOrCountFailure(previous)
                return@forEach
            }
            if (refreshed.communityIntelligence != "available") {
                failures++
                markAttemptedOrCountFailure(previous)
                return@forEach
            }
            if (!store.upsert(refreshed)) {
                failures++
                return@forEach
            }
            if (riskRank(refreshed.riskState) > riskRank(previous.activeRiskState())) {
                escalated += refreshed
            }
        }

        if (
            escalated.isNotEmpty() &&
            CollectiveDefensePreferences(context).notificationsEnabled
        ) {
            CollectiveDefenseNotificationHelper.notifyRiskIncrease(
                context = context,
                count = escalated.size,
                highestRiskState = escalated.maxBy { riskRank(it.riskState) }.riskState
            )
        }

        if (failures > 0 && runAttemptCount < MAX_RETRY_ATTEMPTS) {
            Result.retry()
        } else {
            Result.success()
        }
    }

    companion object {
        private const val MAX_RECHECKS_PER_RUN = 25
        private const val MAX_RETRY_ATTEMPTS = 1

        internal fun riskRank(state: String): Int = when (state) {
            "HIGH_CONFIDENCE" -> 3
            "SUSPICIOUS" -> 2
            "OBSERVED" -> 1
            else -> 0
        }
    }
}
