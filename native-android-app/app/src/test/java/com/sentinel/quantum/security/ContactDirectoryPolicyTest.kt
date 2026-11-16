package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactDirectoryPolicyTest {
    @Test fun contactsWithoutPhoneRowsRemainVisible() {
        val result = ContactDirectoryPolicy.merge(
            contacts = listOf(
                ContactDirectoryPolicy.ContactSeed(1, "Alice", true),
                ContactDirectoryPolicy.ContactSeed(2, "Bob", false)
            ),
            phones = listOf(
                ContactDirectoryPolicy.PhoneSeed(1, "+33 6 10 20 30 40", "+33610203040")
            )
        )

        assertEquals(listOf("Alice", "Bob"), result.map { it.displayName })
        assertEquals(listOf("+33 6 10 20 30 40"), result.first().phoneNumbers)
        assertTrue(result.last().phoneNumbers.isEmpty())
    }

    @Test fun duplicateProviderRowsDoNotDuplicateTheSameNumber() {
        val result = ContactDirectoryPolicy.merge(
            contacts = listOf(ContactDirectoryPolicy.ContactSeed(7, "Charlie", true)),
            phones = listOf(
                ContactDirectoryPolicy.PhoneSeed(7, "06 11 22 33 44", "0611223344"),
                ContactDirectoryPolicy.PhoneSeed(7, "06-11-22-33-44", "0611223344")
            )
        )

        assertEquals(listOf("06 11 22 33 44"), result.single().phoneNumbers)
    }

    @Test fun multipleNumbersStayGroupedUnderOneContact() {
        val result = ContactDirectoryPolicy.merge(
            contacts = listOf(ContactDirectoryPolicy.ContactSeed(3, "Diane", true)),
            phones = listOf(
                ContactDirectoryPolicy.PhoneSeed(3, "01 23 45 67 89", "0123456789"),
                ContactDirectoryPolicy.PhoneSeed(3, "06 98 76 54 32", "0698765432")
            )
        )

        assertEquals(1, result.size)
        assertEquals(2, result.single().phoneNumbers.size)
    }

    @Test fun limitAppliesToContactsNotPhoneRows() {
        val result = ContactDirectoryPolicy.merge(
            contacts = listOf(
                ContactDirectoryPolicy.ContactSeed(1, "A", true),
                ContactDirectoryPolicy.ContactSeed(2, "B", true)
            ),
            phones = listOf(
                ContactDirectoryPolicy.PhoneSeed(1, "1", "1"),
                ContactDirectoryPolicy.PhoneSeed(1, "2", "2"),
                ContactDirectoryPolicy.PhoneSeed(2, "3", "3")
            ),
            limit = 1
        )

        assertEquals(1, result.size)
        assertEquals(listOf("1", "2"), result.single().phoneNumbers)
    }

    @Test fun providerPhoneMismatchRemainsObservable() {
        val result = ContactDirectoryPolicy.merge(
            contacts = listOf(ContactDirectoryPolicy.ContactSeed(9, "Entreprise", true)),
            phones = emptyList()
        )

        assertTrue(result.single().providerHasPhoneNumber)
        assertTrue(result.single().phoneNumbers.isEmpty())
    }
}
