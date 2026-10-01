package com.sentinel.quantum.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsProviderPersistenceTest {
    private fun sent() = SmsCallbackProgress.record(null, 0, 1, SmsDeliveryStatusBus.Stage.SENT, true)!!

    @Test fun failedSentWriteDoesNotBecomeSuccess() {
        assertFalse(SmsProviderPersistence.persist(sent(), { true }, { false }, { true }))
    }

    @Test fun deliveryWriteIsAttemptedEvenWhenSentWriteFails() {
        val sent = sent()
        val delivered = SmsCallbackProgress.record(sent.state, 0, 1, SmsDeliveryStatusBus.Stage.DELIVERED, true)!!
        var deliveryAttempted = false
        assertFalse(SmsProviderPersistence.persist(delivered, { true }, { false }, {
            deliveryAttempted = true
            true
        }))
        assertTrue(deliveryAttempted)
    }

    @Test fun failedDeliveryWriteAndExceptionsAreVisible() {
        val sent = sent()
        val delivered = SmsCallbackProgress.record(sent.state, 0, 1, SmsDeliveryStatusBus.Stage.DELIVERED, true)!!
        assertFalse(SmsProviderPersistence.persist(delivered, { true }, { true }, { false }))
        assertFalse(SmsProviderPersistence.persist(sent, { true }, { throw SecurityException() }, { true }))
    }

    @Test fun sendFailureUsesOnlyFailedTransition() {
        val failed = SmsCallbackProgress.record(null, 0, 1, SmsDeliveryStatusBus.Stage.SENT, false)!!
        assertTrue(SmsProviderPersistence.persist(failed, { true }, { error("unexpected") }, { error("unexpected") }))
        assertFalse(SmsProviderPersistence.persist(failed, { false }, { true }, { true }))
    }
}

