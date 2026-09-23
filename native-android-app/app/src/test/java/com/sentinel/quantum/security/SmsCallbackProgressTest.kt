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

        val second = SmsCallbackProgress.record(
            first.state, 1, 2, SmsDeliveryStatusBus.Stage.SENT, true
        )!!
        assertTrue(second.allSent)
        assertFalse(second.failed)
    }

    @Test fun anyFailureMakesProgressTerminal() {
        val first = SmsCallbackProgress.record(
            null, 0, 3, SmsDeliveryStatusBus.Stage.SENT, true
        )!!
        val failed = SmsCallbackProgress.record(
            first.state, 1, 3, SmsDeliveryStatusBus.Stage.SENT, false
        )!!
        assertTrue(failed.failed)
        assertFalse(failed.allSent)
        assertFalse(failed.allDelivered)
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
        val delivered1 = SmsCallbackProgress.record(
            delivered0.state, 1, 2, SmsDeliveryStatusBus.Stage.DELIVERED, true
        )!!
        assertTrue(delivered1.allDelivered)
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
