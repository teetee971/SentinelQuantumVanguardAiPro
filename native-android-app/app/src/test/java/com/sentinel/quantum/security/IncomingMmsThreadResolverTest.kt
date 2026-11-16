package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingMmsThreadResolverTest {
    @Test
    fun directIncomingMmsUsesSenderThreadWithoutRequiringSelfNumber() {
        val result = IncomingMmsThreadResolver.resolve(
            sender = "+590690111111",
            toAddresses = listOf("+590690222222"),
            ccAddresses = emptyList(),
            selfAddresses = emptySet()
        )
        assertTrue(result is IncomingMmsThreadResolver.Result.Ready)
        assertEquals(
            setOf("+590690111111"),
            (result as IncomingMmsThreadResolver.Result.Ready).recipients
        )
    }

    @Test
    fun groupMmsFailsClosedWhenSelfIdentityIsUnavailable() {
        val result = IncomingMmsThreadResolver.resolve(
            sender = "+590690111111",
            toAddresses = listOf("+590690222222", "+590690333333"),
            ccAddresses = emptyList(),
            selfAddresses = emptySet()
        )
        assertTrue(result is IncomingMmsThreadResolver.Result.SelfIdentityRequired)
    }

    @Test
    fun groupMmsExcludesSelfAndKeepsOtherParticipants() {
        val result = IncomingMmsThreadResolver.resolve(
            sender = "+590690111111",
            toAddresses = listOf("+590690222222", "+590690333333"),
            ccAddresses = listOf("friend@example.test"),
            selfAddresses = setOf("+590690222222")
        ) as IncomingMmsThreadResolver.Result.Ready

        assertEquals(
            setOf("+590690111111", "+590690333333", "friend@example.test"),
            result.recipients
        )
    }

    @Test
    fun addressComparisonNormalizesPhonesAndEmailCase() {
        assertTrue(IncomingMmsThreadResolver.sameAddress("+590 690 12 34 56", "+590690123456"))
        assertTrue(IncomingMmsThreadResolver.sameAddress("USER@example.test", "user@example.test"))
    }
}
