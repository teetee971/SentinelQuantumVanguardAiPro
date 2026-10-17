package com.sentinel.quantum.intelligence

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Non-blocking publication boundary for Intelligence Core producers.
 *
 * Publication is deliberately bounded: producers never wait for the consumer, overload is
 * observable, and consumer failures are contained inside the sink.
 */
interface IntelligenceEventSink {
    fun publish(event: IntelligenceEvent): PublishResult
}

enum class PublishResult {
    ACCEPTED,
    DUPLICATE,
    OVERFLOW,
    REJECTED
}

class BoundedIntelligenceEventSink(
    capacity: Int,
    private val consumer: (IntelligenceEvent) -> Unit,
    threadName: String = "sentinel-intelligence-events"
) : IntelligenceEventSink, AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val pendingEventIds = ConcurrentHashMap.newKeySet<String>()
    private val overflows = AtomicLong(0L)
    private val consumerFailures = AtomicLong(0L)

    private val executor: ThreadPoolExecutor

    init {
        require(capacity > 0) { "capacity must be positive" }
        executor = ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(capacity),
            { task -> Thread(task, threadName).apply { isDaemon = true } },
            ThreadPoolExecutor.AbortPolicy()
        )
    }

    val overflowCount: Long
        get() = overflows.get()

    val consumerFailureCount: Long
        get() = consumerFailures.get()

    override fun publish(event: IntelligenceEvent): PublishResult {
        if (closed.get()) return PublishResult.REJECTED
        if (!pendingEventIds.add(event.eventId)) return PublishResult.DUPLICATE

        return try {
            executor.execute {
                try {
                    consumer(event)
                } catch (_: Throwable) {
                    consumerFailures.incrementAndGet()
                } finally {
                    pendingEventIds.remove(event.eventId)
                }
            }
            PublishResult.ACCEPTED
        } catch (_: RejectedExecutionException) {
            pendingEventIds.remove(event.eventId)
            if (closed.get() || executor.isShutdown) {
                PublishResult.REJECTED
            } else {
                overflows.incrementAndGet()
                PublishResult.OVERFLOW
            }
        } catch (_: Throwable) {
            pendingEventIds.remove(event.eventId)
            PublishResult.REJECTED
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        executor.shutdownNow()
        pendingEventIds.clear()
    }
}
