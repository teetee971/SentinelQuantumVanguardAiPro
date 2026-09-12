package com.sentinel.quantum.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker.Result
import com.sentinel.quantum.data.OsintRepository

class OsintRefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        return try {
            val repository = OsintRepository()
            repository.fetchRssFeed("https://cisa.gov")
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
