package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactPresentationPolicyTest {
    @Test
    fun collapsesVisualDuplicatesWithoutChangingDisplayedValue() {
        assertEquals(
            listOf("06 26 90 71 95"),
            ContactPresentationPolicy.displayNumbers(
                listOf("06 26 90 71 95", "0626907195")
            )
        )
    }

    @Test
    fun keepsDistinctNumbers() {
        assertEquals(
            listOf("06 26 90 71 95", "01 42 00 00 00"),
            ContactPresentationPolicy.displayNumbers(
                listOf("06 26 90 71 95", "01 42 00 00 00")
            )
        )
    }

    @Test
    fun alphabetSectionNormalizesAccentsAndNonLetters() {
        assertEquals("A", ContactPresentationPolicy.sectionLabel("Alice"))
        assertEquals("E", ContactPresentationPolicy.sectionLabel(" Élodie"))
        assertEquals("#", ContactPresentationPolicy.sectionLabel(". ANUBIS"))
        assertEquals("#", ContactPresentationPolicy.sectionLabel("2cv Club"))
        assertEquals("#", ContactPresentationPolicy.sectionLabel(""))
    }

    @Test
    fun alphabetSectionHandlesSupplementaryPlaneLetters() {
        assertEquals("𐐀", ContactPresentationPolicy.sectionLabel("𐐨 Contact"))
    }

    @Test
    fun sectionOrderingGroupsNormalizedInitialsBeforePagination() {
        val names = listOf("Emma", "Zoé", "Élodie")
        val grouped = names.withIndex()
            .sortedWith(
                compareBy<IndexedValue<String>> {
                    ContactPresentationPolicy.sectionOrderKey(it.value)
                }.thenBy { it.index }
            )
            .map { it.value }

        assertEquals(listOf("Emma", "Élodie", "Zoé"), grouped)
    }

    @Test
    fun filtersAreExplicit() {
        assertTrue(ContactPresentationPolicy.include(true, ContactPresentationPolicy.Filter.CALLABLE))
        assertFalse(ContactPresentationPolicy.include(false, ContactPresentationPolicy.Filter.CALLABLE))
        assertTrue(ContactPresentationPolicy.include(false, ContactPresentationPolicy.Filter.ALL))
        assertTrue(ContactPresentationPolicy.include(false, ContactPresentationPolicy.Filter.WITHOUT_NUMBER))
        assertFalse(ContactPresentationPolicy.include(true, ContactPresentationPolicy.Filter.WITHOUT_NUMBER))
    }
}
