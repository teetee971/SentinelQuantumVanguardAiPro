package com.sentinel.quantum.security

import android.provider.Telephony
import org.junit.Assert.assertEquals
import org.junit.Test

class SmsProviderMessageStateTest {
    @Test fun sentMessageDistinguishesDeliveryStates() {
        assertEquals(
            SmsProviderMessageState.State.SENT,
            SmsProviderMessageState.classify(
                Telephony.Sms.MESSAGE_TYPE_SENT,
                Telephony.Sms.STATUS_NONE
            )
        )
        assertEquals(
            SmsProviderMessageState.State.DELIVERY_PENDING,
            SmsProviderMessageState.classify(
                Telephony.Sms.MESSAGE_TYPE_SENT,
                Telephony.Sms.STATUS_PENDING
            )
        )
        assertEquals(
            SmsProviderMessageState.State.DELIVERED,
            SmsProviderMessageState.classify(
                Telephony.Sms.MESSAGE_TYPE_SENT,
                Telephony.Sms.STATUS_COMPLETE
            )
        )
        assertEquals(
            SmsProviderMessageState.State.DELIVERY_FAILED,
            SmsProviderMessageState.classify(
                Telephony.Sms.MESSAGE_TYPE_SENT,
                Telephony.Sms.STATUS_FAILED
            )
        )
    }

    @Test fun sendFailureRemainsDifferentFromDeliveryFailure() {
        assertEquals(
            SmsProviderMessageState.State.SEND_FAILED,
            SmsProviderMessageState.classify(
                Telephony.Sms.MESSAGE_TYPE_FAILED,
                Telephony.Sms.STATUS_FAILED
            )
        )
    }
}
