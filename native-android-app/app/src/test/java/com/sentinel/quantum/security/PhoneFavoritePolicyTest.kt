package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneFavoritePolicyTest {
    @Test
    fun explicitInternationalRepresentationsCollapseWithoutInventingNationalRegion() {
        val values = listOf("+33612345678", "0033612345678")
            .mapNotNull(CallRuleEngine::normalizeNumber)
            .toSet()
        assertEquals(setOf("+33612345678"), values)
    }

    @Test
    fun nationalRepresentationStaysDistinctUntilARegionIsObserved() {
        val values = listOf("06 12 34 56 78", "+33612345678", "0033612345678")
            .mapNotNull(CallRuleEngine::normalizeNumber)
            .toSet()
        assertEquals(setOf("0612345678", "+33612345678"), values)
    }

    @Test
    fun invalidNumbersFailClosed() {
        assertFalse(isValidFavorite("123"))
        assertFalse(isValidFavorite("06CALLME"))
        assertTrue(isValidFavorite("06 12 34 56 78"))
    }

    private fun isValidFavorite(raw: String?): Boolean =
        CallRuleEngine.normalizeNumber(raw) != null
}
