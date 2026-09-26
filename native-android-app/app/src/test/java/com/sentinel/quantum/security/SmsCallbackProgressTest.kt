package com.sentinel.quantum.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsCallbackProgressTest {
    @Test fun multipartSentWaitsForEveryPart() {
        val first = SmsCallbackProgress.record(
            null, 0, 2, SmsDeliveryStatusBus.Stage.SENT, true
        )!!
        assertFalse(first.allSent)
        assertFalse(first.terminal)

        val second = SmsCallbackProgress.record(
            first.state, 1, 2, SmsDeliveryStatusBus.Stage.SENT, true
        )!!
        assertTrue(second.allSent)
        assertFalse(second.sendFailed)
        assertFalse(second.terminal)
    }

    @Test fun multipartSendFailureWaitsForEverySentCallbackBeforeTerminal() {
        val first = SmsCallbackProgress.record(
            null, 0, 3, SmsDeliveryStatusBus.Stage.SENT, true
        )!!
        val failed = SmsCallbackProgress.record(
            first.state, 1, 3, SmsDeliveryStatusBus.Stage.SENT, false
        )!!
        assertTrue(failed.sendFailed)
        assertFalse(failed.deliveryFailed)
        assertFalse(failed.terminal)
        assertFalse(failed.allSent)
        assertFalse(failed.allDelivered)

        val last = SmsCallbackProgress.record(
            failed.state, 2, 3, SmsDeliveryStatusBus.Stage.SENT, true
        )!!
        assertTrue(last.sendFailed)
        assertTrue(last.terminal)
        assertTrue(last.state.sentOk.containsAll(setOf(0, 2)))
        assertTrue(last.state.sentFailed == setOf(1))
    }

    @Test fun failureFirstStillPreservesLaterMultipartSentCallbacks() {
        val failedFirst = SmsCallbackProgress.record(
            null, 1, 3, SmsDeliveryStatusBus.Stage.SENT, false
        )!!
        assertFalse(failedFirst.terminal)

        val sent0 = SmsCallbackProgress.record(
            failedFirst.state, 0, 3, SmsDeliveryStatusBus.Stage.SENT, true
        )!!
        assertFalse(sent0.terminal)

        val sent2 = SmsCallbackProgress.record(
            sent0.state, 2, 3, SmsDeliveryStatusBus.Stage.SENT, true
        )!!
        assertTrue(sent2.terminal)
        assertTrue(sent2.sendFailed)
        assertTrue(sent2.state.sentOk == setOf(0, 2))
        assertTrue(sent2.state.sentFailed == setOf(1))
    }

    @Test fun deliveryFailureDoesNotTurnIntoSendFailure() {
        val sent0 = SmsCallbackProgress.record(
            null, 0, 2, SmsDeliveryStatusBus.Stage.SENT, true
        )!!
        val sent1 = SmsCallbackProgress.record(
            sent0.state, 1, 2, SmsDeliveryStatusBus.Stage.SENT, true
        )!!
        val deliveryFailed0 = SmsCallbackProgress.record(
            sent1.state, 0, 2, SmsDeliveryStatusBus.Stage.DELIVERED, false
        )!!
        assertTrue(deliveryFailed0.allSent)
        assertFalse(deliveryFailed0.sendFailed)
        assertTrue(deliveryFailed0.deliveryFailed)
        assertFalse(deliveryFailed0.terminal)

        val delivered1 = SmsCallbackProgress.record(
            deliveryFailed0.state, 1, 2, SmsDeliveryStatusBus.Stage.DELIVERED, true
        )!!
        assertTrue(delivered1.allSent)
        assertFalse(delivered1.sendFailed)
        assertTrue(delivered1.deliveryFailed)
        assertFalse(delivered1.allDelivered)
        assertTrue(delivered1.terminal)
    }

    @Test fun duplicateCallbacksAreIdempotent() {
        val first = SmsCallbackProgress.record(
            null, 0, 2, SmsDeliveryStatusBus.Stage.SENT, true
        )!!
        val duplicate = SmsCallbackProgress.record(
            first.state, 0, 2, SmsDeliveryStatusBus.Stage.SENT, true
        )!!
        assertFalse(duplicate.allSent)
        assertTrue(duplicate.state.sentOk.size == 1)
        assertTrue(duplicate.state.sentFailed.isEmpty())
    }

    @Test fun deliveryCompletesOnlyAfterEveryPart() {
        val sent0 = SmsCallbackProgress.record(
            null, 0, 2, SmsDeliveryStatusBus.Stage.SENT, true
        )!!
        val sent1 = SmsCallbackProgress.record(
            sent0.state, 1, 2, SmsDeliveryStatusBus.Stage.SENT, true
        )!!
        val delivered0 = SmsCallbackProgress.record(
            sent1.state, 0, 2, SmsDeliveryStatusBus.Stage.DELIVERED, true
        )!!
        assertFalse(delivered0.allDelivered)
        assertFalse(delivered0.terminal)
        val delivered1 = SmsCallbackProgress.record(
            delivered0.state, 1, 2, SmsDeliveryStatusBus.Stage.DELIVERED, true
        )!!
        assertTrue(delivered1.allDelivered)
        assertTrue(delivered1.terminal)
        assertFalse(delivered1.deliveryFailed)
    }

    @Test fun rejectsPartCountMismatchAndOutOfRangeIndex() {
        val first = SmsCallbackProgress.record(
            null, 0, 2, SmsDeliveryStatusBus.Stage.SENT, true
        )!!
        assertNull(
            SmsCallbackProgress.record(
                first.state, 1, 3, SmsDeliveryStatusBus.Stage.SENT, true
            )
        )
        assertNull(
            SmsCallbackProgress.record(
                null, 2, 2, SmsDeliveryStatusBus.Stage.SENT, true
            )
        )
    }
}
