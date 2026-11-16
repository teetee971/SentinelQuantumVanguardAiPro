package com.sentinel.quantum.intelligence

import com.sentinel.quantum.integration.IntegrationCapabilityId
import com.sentinel.quantum.integration.IntegrationState
import com.sentinel.quantum.integration.IntegrationTruthRuntime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntelligenceEventSinkTest {
    @Test
    fun publish_returns_promptly_while_consumer_is_blocked() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        BoundedIntelligenceEventSink(capacity = 1, consumer = {
            entered.countDown()
            release.await(2, TimeUnit.SECONDS)
        }).use { sink ->
            val result = sink.publish(event("evt-blocked"))
            assertEquals(PublishResult.ACCEPTED, result)
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            release.countDown()
        }
    }

    @Test
    fun queue_capacity_is_bounded_and_overflow_is_observable() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        BoundedIntelligenceEventSink(capacity = 1, consumer = {
            entered.countDown()
            release.await(2, TimeUnit.SECONDS)
        }).use { sink ->
            assertEquals(PublishResult.ACCEPTED, sink.publish(event("evt-running")))
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            assertEquals(PublishResult.ACCEPTED, sink.publish(event("evt-queued")))
            assertEquals(PublishResult.OVERFLOW, sink.publish(event("evt-overflow")))
            assertEquals(1L, sink.overflowCount)
            release.countDown()
        }
    }

    @Test
    fun duplicate_pending_event_id_is_not_enqueued_twice() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        BoundedIntelligenceEventSink(capacity = 1, consumer = {
            entered.countDown()
            release.await(2, TimeUnit.SECONDS)
        }).use { sink ->
            val first = event("evt-duplicate")
            assertEquals(PublishResult.ACCEPTED, sink.publish(first))
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            assertEquals(PublishResult.DUPLICATE, sink.publish(first))
            release.countDown()
        }
    }

    @Test
    fun consumer_failure_never_escapes_publish_and_is_counted() {
        val attempted = CountDownLatch(1)
        BoundedIntelligenceEventSink(capacity = 1, consumer = {
            attempted.countDown()
            throw IllegalStateException("consumer failed")
        }).use { sink ->
            assertEquals(PublishResult.ACCEPTED, sink.publish(event("evt-failure")))
            assertTrue(attempted.await(1, TimeUnit.SECONDS))
            assertTrue(waitUntil { sink.consumerFailureCount == 1L })
        }
    }

    @Test
    fun closed_sink_rejects_without_throwing_into_producer() {
        val sink = BoundedIntelligenceEventSink(capacity = 1, consumer = {})
        sink.close()
        assertEquals(PublishResult.REJECTED, sink.publish(event("evt-after-close")))
    }

    @Test
    fun successful_consumer_publishes_fresh_runtime_truth() {
        IntegrationTruthRuntime.clearForTests()
        val consumed = CountDownLatch(1)
        BoundedIntelligenceEventSink(capacity = 1, consumer = {
            consumed.countDown()
        }).use { sink ->
            assertEquals(PublishResult.ACCEPTED, sink.publish(event("evt-truth")))
            assertTrue(consumed.await(1, TimeUnit.SECONDS))
            assertTrue(waitUntil {
                IntegrationTruthRuntime.snapshot(nowMs = 1_001L)
                    .status(IntegrationCapabilityId.CALL_SCREENING).state == IntegrationState.CONNECTED
            })
        }
    }

    private fun event(id: String): IntelligenceEvent = IntelligenceEvent(
        eventId = id,
        identityKey = SentinelIdentityKey.phoneFingerprint("fp:v1:$id", 1),
        evidence = IntelligenceEvidence.CallDecision(
            action = IntelligenceEvidence.CallAction.ALLOW,
            ruleSource = "LOCAL_RULE_ENGINE",
            reasonCode = "NO_MATCH",
            networkVerification = IntelligenceEvidence.NetworkVerification.UNKNOWN
        ),
        observedAtMs = 1_000L,
        expiresAtMs = 2_000L,
        source = EvidenceSource.CALL_SCREENING,
        provenance = EvidenceProvenance(
            producer = EvidenceProvenance.Producer.PHONE_CORE,
            producerVersion = 1,
            trust = EvidenceProvenance.TrustClass.LOCAL_DERIVED
        ),
        privacyClass = PrivacyClass.BOUNDED_METADATA
    )

    private fun waitUntil(timeoutMs: Long = 1_000L, condition: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (System.nanoTime() < deadline) {
            if (condition()) return true
            Thread.sleep(5)
        }
        return condition()
    }
}
