package com.sentinel.quantum.security

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Single-worker executor with a bounded queue for work that happens only after the mandatory
 * CallScreeningService response has already been delivered to Android.
 *
 * The caller deliberately uses AbortPolicy: overload must drop optional post-response telemetry
 * instead of growing memory without bound or delaying the platform screening callback.
 */
internal object BoundedPostResponseExecutor {
    const val DEFAULT_QUEUE_CAPACITY: Int = 64

    fun create(
        threadName: String,
        queueCapacity: Int = DEFAULT_QUEUE_CAPACITY
    ): ThreadPoolExecutor {
        require(queueCapacity > 0) { "queueCapacity must be positive" }
        return ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(queueCapacity),
            { task -> Thread(task, threadName).apply { isDaemon = true } },
            ThreadPoolExecutor.AbortPolicy()
        )
    }
}
