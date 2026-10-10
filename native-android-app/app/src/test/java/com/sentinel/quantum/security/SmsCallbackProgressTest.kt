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
        assertFalse(first.submissionResolved)
        assertFalse(first.terminal)

        val second = SmsCallbackProgress.record(
            first.state, 1, 2, SmsDeliveryStatusBus.Stage.SENT, true
        )!!
        assertTrue(second.allSent)
        assertTrue(second.submissionResolved)
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
        assertFalse(failed.submissionResolved)
        assertFalse(failed.deliveryFailed)
        assertFalse(failed.terminal)
        assertFalse(failed.allSent)
        assertFalse(failed.allDelivered)

        val last = SmsCallbackProgress.record(
            failed.state, 2, 3, SmsDeliveryStatusBus.Stage.SENT, true
        )!!
        assertTrue(last.sendFailed)
        assertTrue(last.submissionResolved)
        assertTrue(last.terminal)
        assertTrue(last.state.sentOk.containsAll(setOf(0, 2)))
        assertTrue(last.state.sentFailed == setOf(1))
    }

    @Test fun failureFirstStillPreservesLaterMultipartSentCallbacks() {
        val failedFirst = SmsCallbackProgress.record(
            null, 1, 3, SmsDeliveryStatusBus.Stage.SENT, false
        )!!
        assertFalse(failedFirst.submissionResolved)
        assertFalse(failedFirst.terminal)

        val sent0 = SmsCallbackProgress.record(
            failedFirst.state, 0, 3, SmsDeliveryStatusBus.Stage.SENT, true
        )!!
        assertFalse(sent0.submissionResolved)
        assertFalse(sent0.terminal)

        val sent2 = SmsCallbackProgress.record(
            sent0.state, 2, 3, SmsDeliveryStatusBus.Stage.SENT, true
        )!!
        assertTrue(sent2.submissionResolved)
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
        assertTrue(deliveryFailed0.submissionResolved)
        assertTrue(deliveryFailed0.allSent)
        assertFalse(deliveryFailed0.sendFailed)
        assertTrue(deliveryFailed0.deliveryFailed)
        assertFalse(deliveryFailed0.terminal)

        val delivered1 = SmsCallbackProgress.record(
            deliveryFailed0.state, 1, 2, SmsDeliveryStatusBus.Stage.DELIVERED, true
        )!!
        assertTrue(delivered1.submissionResolved)
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
        )
        assertNull(duplicate)
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

    @Test fun statusBusReplaysFastPiiFreeCallbackForLateCollector() {
        val event = SmsDeliveryStatusBus.Event(
            sendToken = Int.MAX_VALUE - 17,
            providerMessageId = Long.MAX_VALUE - 17L,
            partIndex = 0,
            partCount = 1,
            stage = SmsDeliveryStatusBus.Stage.SENT,
            successful = true
        )
        SmsDeliveryStatusBus.publish(event)
        assertTrue(SmsDeliveryStatusBus.events.replayCache.contains(event))
        assertTrue(
            SmsDeliveryStatusBus.CALLBACK_REPLAY_CAPACITY >= SmsCallbackProgress.MAX_PARTS * 2
        )
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

    @Test fun deliveryBeforeFinalSentCallbackPreservesBothCertificationSignals() {
        val delivered0 = SmsCallbackProgress.record(
            null, 0, 2, SmsDeliveryStatusBus.Stage.DELIVERED, true
        )!!
        val delivered1 = SmsCallbackProgress.record(
            delivered0.state, 1, 2, SmsDeliveryStatusBus.Stage.DELIVERED, true
        )!!
        assertTrue(delivered1.certificationSignals.isEmpty())
        val sent0 = SmsCallbackProgress.record(
            delivered1.state, 0, 2, SmsDeliveryStatusBus.Stage.SENT, true
        )!!
        assertTrue(sent0.certificationSignals.isEmpty())
        val sent1 = SmsCallbackProgress.record(
            sent0.state, 1, 2, SmsDeliveryStatusBus.Stage.SENT, true
        )!!
        assertTrue(sent1.submissionResolved)
        assertTrue(sent1.allDelivered)
        assertTrue(sent1.terminal)
        assertTrue(sent1.certificationSignals == listOf(
            PhoneCorePhysicalValidation.SIGNAL_SMS_ALL_PARTS_SENT,
            PhoneCorePhysicalValidation.SIGNAL_SMS_ALL_PARTS_DELIVERED
        ))
    }

    @Test fun failedMultipartDeliveryNeverEmitsSuccessfulDeliveryProof() {
        val sent = SmsCallbackProgress.record(
            null, 0, 1, SmsDeliveryStatusBus.Stage.SENT, true
        )!!
        val failed = SmsCallbackProgress.record(
            sent.state, 0, 1, SmsDeliveryStatusBus.Stage.DELIVERED, false
        )!!
        assertTrue(sent.submissionResolved)
        assertTrue(sent.certificationSignals == listOf(
            PhoneCorePhysicalValidation.SIGNAL_SMS_ALL_PARTS_SENT
        ))
        assertTrue(failed.certificationSignals.isEmpty())
    }

    @Test fun fiftyPartDeliveryEmitsOnlyOneSentAndOneDeliveredProof() {
        var state: SmsCallbackProgress.State? = null
        val signals = mutableListOf<String>()
        for (index in 0 until 50) {
            val outcome = SmsCallbackProgress.record(
                state, index, 50, SmsDeliveryStatusBus.Stage.SENT, true
            )!!
            state = outcome.state
            signals += outcome.certificationSignals
        }
        for (index in 0 until 50) {
            val outcome = SmsCallbackProgress.record(
                state, index, 50, SmsDeliveryStatusBus.Stage.DELIVERED, true
            )!!
            state = outcome.state
            signals += outcome.certificationSignals
        }
        assertTrue(signals == listOf(
            PhoneCorePhysicalValidation.SIGNAL_SMS_ALL_PARTS_SENT,
            PhoneCorePhysicalValidation.SIGNAL_SMS_ALL_PARTS_DELIVERED
        ))
    }

    @Test fun duplicateFinalSentCallbackDoesNotRepeatCertificationProof() {
        val sent = SmsCallbackProgress.record(
            null, 0, 1, SmsDeliveryStatusBus.Stage.SENT, true
        )!!
        assertTrue(sent.submissionResolved)
        val duplicate = SmsCallbackProgress.record(
            sent.state, 0, 1, SmsDeliveryStatusBus.Stage.SENT, true
        )
        assertNull(duplicate)
    }

    @Test fun lateSuccessCannotEraseMultipartSendFailure() {
        val failed = SmsCallbackProgress.record(null, 0, 2, SmsDeliveryStatusBus.Stage.SENT, false)!!
        assertFalse(failed.submissionResolved)
        assertNull(SmsCallbackProgress.record(failed.state, 0, 2, SmsDeliveryStatusBus.Stage.SENT, true))
        val last = SmsCallbackProgress.record(failed.state, 1, 2, SmsDeliveryStatusBus.Stage.SENT, true)!!
        assertTrue(last.submissionResolved)
        assertTrue(last.sendFailed)
        assertTrue(last.terminal)
        assertFalse(last.allSent)
        assertTrue(last.certificationSignals.isEmpty())
    }

    @Test fun lateSuccessCannotEraseMultipartDeliveryFailure() {
        val sent0 = SmsCallbackProgress.record(null, 0, 2, SmsDeliveryStatusBus.Stage.SENT, true)!!
        val sent1 = SmsCallbackProgress.record(sent0.state, 1, 2, SmsDeliveryStatusBus.Stage.SENT, true)!!
        val failed = SmsCallbackProgress.record(sent1.state, 0, 2, SmsDeliveryStatusBus.Stage.DELIVERED, false)!!
        assertNull(SmsCallbackProgress.record(failed.state, 0, 2, SmsDeliveryStatusBus.Stage.DELIVERED, true))
        val last = SmsCallbackProgress.record(failed.state, 1, 2, SmsDeliveryStatusBus.Stage.DELIVERED, true)!!
        assertTrue(last.submissionResolved)
        assertTrue(last.deliveryFailed)
        assertFalse(last.allDelivered)
        assertTrue(last.certificationSignals.isEmpty())
    }

    @Test fun duplicateFailureIsIgnoredAndFailureCanDowngradePriorSuccess() {
        val sent = SmsCallbackProgress.record(null, 0, 2, SmsDeliveryStatusBus.Stage.SENT, true)!!
        val failed = SmsCallbackProgress.record(sent.state, 0, 2, SmsDeliveryStatusBus.Stage.SENT, false)!!
        assertTrue(failed.sendFailed)
        assertFalse(failed.submissionResolved)
        assertNull(SmsCallbackProgress.record(failed.state, 0, 2, SmsDeliveryStatusBus.Stage.SENT, false))
    }

    @Test fun maximumSupportedMultipartMessageRetainsEveryObservedCallback() {
        val partCount = SmsCallbackProgress.MAX_PARTS
        var state: SmsCallbackProgress.State? = null
        var sentSignals = 0
        var deliveredSignals = 0
        for (stage in listOf(SmsDeliveryStatusBus.Stage.SENT, SmsDeliveryStatusBus.Stage.DELIVERED)) {
            for (index in 0 until partCount) {
                val outcome = SmsCallbackProgress.record(state, index, partCount, stage, true)!!
                state = outcome.state
                if (outcome.sentCompletedNow) sentSignals++
                if (outcome.deliveryCompletedNow) deliveredSignals++
                if (stage == SmsDeliveryStatusBus.Stage.SENT) {
                    assertTrue(outcome.submissionResolved == (index == partCount - 1))
                } else {
                    assertTrue(outcome.submissionResolved)
                }
                assertTrue(outcome.allDelivered == (stage == SmsDeliveryStatusBus.Stage.DELIVERED && index == partCount - 1))
            }
        }
        assertTrue(state!!.sentOk.size == partCount)
        assertTrue(state!!.deliveredOk.size == partCount)
        assertTrue(sentSignals == 1)
        assertTrue(deliveredSignals == 1)
    }
}
