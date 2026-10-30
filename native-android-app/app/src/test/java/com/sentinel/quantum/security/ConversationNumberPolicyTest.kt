package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConversationNumberPolicyTest {
    @Test fun visualFormattingIsRemovedWithoutGuessingACountry() {
        assertEquals("+33612345678", ConversationNumberPolicy.number("+33 (6) 12-34-56-78"))
        assertEquals("0612345678", ConversationNumberPolicy.number("06 12 34 56 78"))
    }
    @Test fun senderLabelsAndMissingAddressesHaveNoNumberActions() {
        listOf(null, "", "   ", "BANQUE", "Service client").forEach {
            assertNull(ConversationNumberPolicy.number(it))
        }
    }
    @Test fun serviceCodesAndDialControlCharactersAreNotForwarded() {
        listOf("*123#", "+33612345678,123", "+33612345678;123", "06 12 ext 9").forEach {
            assertNull(ConversationNumberPolicy.number(it))
        }
    }
    @Test fun uriPayloadsAndMultipleRecipientsAreNotGuessed() {
        listOf("tel:+33612345678", "+33612345678?x=1", "+33612345678#x", "06;07", "06,07", "06\n07", "a@example.org").forEach {
            assertNull(ConversationNumberPolicy.number(it))
        }
    }
    @Test fun malformedPlusUnicodeAndOversizedAddressesAreRejected() {
        listOf("++33612345678", "0612+345678", "+", "١٢٣٤", "1".repeat(33), " ".repeat(129)).forEach {
            assertNull(ConversationNumberPolicy.number(it))
        }
    }
}
