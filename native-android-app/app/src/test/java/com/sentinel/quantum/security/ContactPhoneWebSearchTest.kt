package com.sentinel.quantum.security

import org.junit.Assert.*
import org.junit.Test
import java.net.URI
import java.net.URLDecoder

class ContactPhoneWebSearchTest {
    @Test fun formatsOneSelectedNumberAsQuotedQueryWithoutAddingIdentityData() {
        assertEquals("+12025550123", ContactPhoneWebSearch.normalized(" +1 (202) 555-0123 "))
        val url = ContactPhoneWebSearch.url(" +1 (202) 555-0123 ")!!
        assertEquals("duckduckgo.com", URI(url).host)
        assertEquals("q=\"+12025550123\"", URLDecoder.decode(URI(url).rawQuery, "UTF-8"))
        assertFalse(url.contains("#"))
    }

    @Test fun localAndInternationalPrefixesArePreservedWithoutGuessingCountry() {
        assertEquals("0123456789", ContactPhoneWebSearch.normalized("01 23 45 67 89"))
        assertEquals("0012025550123", ContactPhoneWebSearch.normalized("00 1 202 555 0123"))
        assertEquals("q=\"0123456789\"", URLDecoder.decode(URI(ContactPhoneWebSearch.url("0123456789")!!).rawQuery, "UTF-8"))
    }

    @Test fun rejectsExtensionsCommandsQueryInjectionControlsAndInvalidLengths() {
        listOf("", "112", "1234", "1234567890123456", "+12025550123;1", "+12025550123,1", "*12345#", "++12025550123", "1202+5550123", "12345 OR site:example.invalid", "12345&x=1", "12345\n", "x".repeat(129)).forEach {
            assertNull(it, ContactPhoneWebSearch.url(it))
        }
    }
}
