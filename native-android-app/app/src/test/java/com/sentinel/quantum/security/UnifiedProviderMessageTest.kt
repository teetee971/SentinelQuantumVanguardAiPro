package com.sentinel.quantum.security

import android.provider.Telephony
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedProviderMessageTest {
    @Test
    fun mmsIdsUseANonCollidingNegativeNamespace() {
        val encoded = UnifiedProviderMessage.encodeMmsId(42L)
        assertEquals(-42L, encoded)
        assertEquals(42L, UnifiedProviderMessage.decodeMmsId(encoded!!))
        assertNull(UnifiedProviderMessage.encodeMmsId(0L))
        assertNull(UnifiedProviderMessage.decodeMmsId(42L))
        assertNull(UnifiedProviderMessage.decodeMmsId(Long.MIN_VALUE))
    }

    @Test
    fun mmsDatesConvertSecondsWithoutOverflow() {
        assertEquals(1_700_000_000_000L, UnifiedProviderMessage.mmsDateMs(1_700_000_000L))
        assertEquals(-1L, UnifiedProviderMessage.mmsDateMs(-1L))
        assertEquals(Long.MAX_VALUE, UnifiedProviderMessage.mmsDateMs(Long.MAX_VALUE))
    }

    @Test
    fun sentMmsNeverManufacturesRecipientDelivery() {
        val state = UnifiedProviderMessage.mmsBoxToSmsEquivalent(Telephony.Mms.MESSAGE_BOX_SENT)
        assertEquals(Telephony.Sms.MESSAGE_TYPE_SENT, state.type)
        assertEquals(Telephony.Sms.STATUS_NONE, state.status)
        assertTrue(state.status != Telephony.Sms.STATUS_COMPLETE)
    }

    @Test
    fun mmsProviderBoxesRemainFactuallyDistinct() {
        val inbox = UnifiedProviderMessage.mmsBoxToSmsEquivalent(Telephony.Mms.MESSAGE_BOX_INBOX)
        val outbox = UnifiedProviderMessage.mmsBoxToSmsEquivalent(Telephony.Mms.MESSAGE_BOX_OUTBOX)
        val failed = UnifiedProviderMessage.mmsBoxToSmsEquivalent(Telephony.Mms.MESSAGE_BOX_FAILED)
        val draft = UnifiedProviderMessage.mmsBoxToSmsEquivalent(Telephony.Mms.MESSAGE_BOX_DRAFTS)

        assertEquals(Telephony.Sms.MESSAGE_TYPE_INBOX, inbox.type)
        assertEquals(Telephony.Sms.MESSAGE_TYPE_OUTBOX, outbox.type)
        assertEquals(Telephony.Sms.STATUS_PENDING, outbox.status)
        assertEquals(Telephony.Sms.MESSAGE_TYPE_FAILED, failed.type)
        assertEquals(Telephony.Sms.STATUS_FAILED, failed.status)
        assertEquals(Telephony.Sms.MESSAGE_TYPE_DRAFT, draft.type)
    }
}
