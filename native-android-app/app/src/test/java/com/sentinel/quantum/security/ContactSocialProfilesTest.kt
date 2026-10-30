package com.sentinel.quantum.security

import org.junit.Assert.*
import org.junit.Test
import java.net.URI
import java.net.URLDecoder

class ContactSocialProfilesTest {
    @Test fun onlyRealProfileRoutesOnExpectedHostsAreAccepted() {
        assertEquals("https://www.linkedin.com/in/fixture-person/", ContactSocialProfiles.canonical(SocialNetwork.LINKEDIN, "https://linkedin.com/in/fixture-person?trk=test"))
        assertEquals("https://www.instagram.com/fixture.person/", ContactSocialProfiles.canonical(SocialNetwork.INSTAGRAM, "https://instagram.com/fixture.person/"))
        assertEquals("https://x.com/fixture_user", ContactSocialProfiles.canonical(SocialNetwork.X, "https://twitter.com/fixture_user"))
    }

    @Test fun rejectsNonProfilesCrossDomainsCredentialsAndUnsafeSchemes() {
        val bad = listOf("http://x.com/fixture", "https://x.com.evil.invalid/fixture", "https://user@x.com/fixture", "https://x.com:8443/fixture", "https://x.com/search", "https://x.com/home", "https://x.com/fixture/status/1", "https://x.com/fixture%2Fother", "javascript:alert(1)")
        bad.forEach { assertNull(it, ContactSocialProfiles.canonical(SocialNetwork.X, it)) }
        listOf("https://www.linkedin.com/company/example", "https://www.linkedin.com/in/a/b", "https://www.linkedin.com/in/%0a").forEach {
            assertNull(it, ContactSocialProfiles.canonical(SocialNetwork.LINKEDIN, it))
        }
        listOf("https://instagram.com/p/example", "https://instagram.com/accounts/", "https://instagram.com/explore").forEach {
            assertNull(it, ContactSocialProfiles.canonical(SocialNetwork.INSTAGRAM, it))
        }
    }

    @Test fun encodedDotSegmentsAreNeverInstagramProfiles() {
        listOf("https://instagram.com/./", "https://instagram.com/../", "https://instagram.com/%2e%2e/").forEach {
            assertNull(it, ContactSocialProfiles.canonical(SocialNetwork.INSTAGRAM, it))
        }
    }

    @Test fun corruptNumericTypeAndDuplicateOwnerNetworkFailClosed() {
        val owner = ContactSocialProfiles.ownerKey("fixture")!!
        val row = org.json.JSONObject().put("owner", owner).put("network", "X").put("url", "https://x.com/fixture")
            .put("summary", "").put("source", "USER_CONFIRMED_LINK").put("associatedAtMs", "100")
        var raw: String? = org.json.JSONObject().put("version", 1).put("links", org.json.JSONArray().put(row)).toString()
        val store = ContactSocialProfileStore({ raw }, { raw = it; true })
        assertFalse(store.read(owner).available)
        row.put("associatedAtMs", 100L)
        raw = org.json.JSONObject().put("version", 1).put("links", org.json.JSONArray().put(row).put(row)).toString()
        val before = raw
        assertFalse(store.associate(owner, SocialNetwork.X, "https://x.com/new_fixture", "", 200, true))
        assertEquals(before, raw)
    }

    @Test fun networkQueryUsesOnlyExplicitNameCompanyAndCorrectEncoding() {
        val uri = URI(ContactSocialProfiles.searchUrl(SocialNetwork.LINKEDIN, "Fixture & Name", "Test Company")!!)
        assertEquals("duckduckgo.com", uri.host)
        assertEquals("q=site:linkedin.com/in/ \"Fixture & Name\" \"Test Company\"", URLDecoder.decode(uri.rawQuery, "UTF-8"))
        assertNull(ContactSocialProfiles.searchUrl(SocialNetwork.X, " ", "Company"))
        assertNull(ContactSocialProfiles.searchUrl(SocialNetwork.X, "x".repeat(121), ""))
        assertNull(ContactSocialProfiles.searchUrl(SocialNetwork.X, "Fixture\nOther", ""))
    }

    @Test fun lookupOwnerIsRequiredAndDifferentLookupKeysNeverAlias() {
        assertNull(ContactSocialProfiles.ownerKey(null))
        assertNull(ContactSocialProfiles.ownerKey(""))
        assertEquals(64, ContactSocialProfiles.ownerKey("lookup-A")!!.length)
        assertNotEquals(ContactSocialProfiles.ownerKey("lookup-A"), ContactSocialProfiles.ownerKey("lookup-B"))
    }

    @Test fun onlyConfirmedLinksArePersistedAndOtherOwnersAreUntouched() {
        var raw: String? = null
        val store = ContactSocialProfileStore({ raw }, { raw = it; true })
        val a = ContactSocialProfiles.ownerKey("owner-A")!!
        val b = ContactSocialProfiles.ownerKey("owner-B")!!
        assertFalse(store.associate(a, SocialNetwork.X, "https://x.com/fixture_a", "summary", 100, confirmed = false))
        assertTrue(store.read(a).links.isEmpty())
        assertTrue(store.associate(a, SocialNetwork.X, "https://x.com/fixture_a", " manually supplied ", 100, confirmed = true))
        assertTrue(store.associate(b, SocialNetwork.X, "https://x.com/fixture_b", "", 200, confirmed = true))
        val restored = ContactSocialProfileStore({ raw }, { raw = it; true })
        assertEquals("https://x.com/fixture_a", restored.read(a).links.single().url)
        assertEquals("manually supplied", restored.read(a).links.single().summary)
        assertEquals(100L, restored.read(a).links.single().associatedAtMs)
        assertEquals("https://x.com/fixture_b", restored.read(b).links.single().url)
        assertTrue(restored.remove(a, SocialNetwork.X))
        assertTrue(restored.read(a).links.isEmpty())
        assertEquals(1, restored.read(b).links.size)
    }

    @Test fun updatesOnlyChosenNetworkAndRejectsBadInputWithoutChangingBook() {
        var raw: String? = null
        val store = ContactSocialProfileStore({ raw }, { raw = it; true })
        val a = ContactSocialProfiles.ownerKey("owner-A")!!
        assertTrue(store.associate(a, SocialNetwork.X, "https://x.com/fixture_a", "", 100, true))
        assertTrue(store.associate(a, SocialNetwork.LINKEDIN, "https://linkedin.com/in/fixture", "", 101, true))
        assertTrue(store.associate(a, SocialNetwork.X, "https://x.com/fixture_new", "updated", 200, true))
        assertEquals(2, store.read(a).links.size)
        val before = raw
        assertFalse(store.associate(a, SocialNetwork.X, "https://evil.invalid/fixture", "", 201, true))
        assertFalse(store.associate(a, SocialNetwork.X, "https://x.com/fixture", "x".repeat(2001), 201, true))
        assertFalse(store.associate(a, SocialNetwork.X, "https://x.com/fixture", "", 0, true))
        assertEquals(before, raw)
    }

    @Test fun malformedOrUnavailableStorageCannotBeOverwrittenByAssociation() {
        var raw: String? = "invalid JSON"
        val store = ContactSocialProfileStore({ raw }, { raw = it; true })
        val a = ContactSocialProfiles.ownerKey("owner-A")!!
        assertFalse(store.read(a).available)
        assertFalse(store.associate(a, SocialNetwork.X, "https://x.com/fixture", "", 100, true))
        assertEquals("invalid JSON", raw)
        val failed = ContactSocialProfileStore({ throw IllegalStateException("fixture") }, { error("Must not write") })
        assertFalse(failed.read(a).available)
        assertFalse(failed.remove(a, SocialNetwork.X))
        val writeFailed = ContactSocialProfileStore({ null }, { false })
        assertFalse(writeFailed.associate(a, SocialNetwork.X, "https://x.com/fixture", "", 100, true))
    }

    @Test fun failedAtomicReplacementPreservesOldFileAndRemovesTemporaryData() {
        val directory = java.nio.file.Files.createTempDirectory("social-profile-fixture-").toFile()
        try {
            val file = ContactSocialProfileFile(directory)
            assertTrue(file.write("old book"))
            val deniedReplacement = ContactSocialProfileFile(directory) { _, _ -> false }
            assertFalse(deniedReplacement.write("new book"))
            assertEquals("old book", file.read())
            assertEquals(listOf("sentinel_contact_profiles_v1.json"), directory.list()!!.toList())
        } finally { directory.deleteRecursively() }
    }

    @Test fun atomicFileRoundTripIsBoundedAndNeverReplacesOnOversize() {
        val directory = java.nio.file.Files.createTempDirectory("social-profile-fixture-").toFile()
        try {
            val file = ContactSocialProfileFile(directory)
            assertNull(file.read())
            assertTrue(file.write("fixture"))
            assertFalse(file.write("x".repeat(1024 * 1024 + 1)))
            assertEquals("fixture", file.read())
        } finally { directory.deleteRecursively() }
    }

    @Test fun fullBookPreservesExistingLinksAndStillAllowsReplacementAndRemoval() {
        var raw: String? = null
        val store = ContactSocialProfileStore({ raw }, { raw = it; true })
        repeat(200) { n -> assertTrue(store.associate(ContactSocialProfiles.ownerKey("owner-$n")!!, SocialNetwork.X, "https://x.com/fixture", "", 100, true)) }
        val before = raw
        assertFalse(store.associate(ContactSocialProfiles.ownerKey("owner-new")!!, SocialNetwork.X, "https://x.com/fixture", "", 100, true))
        assertEquals(before, raw)
        val first = ContactSocialProfiles.ownerKey("owner-0")!!
        assertTrue(store.associate(first, SocialNetwork.X, "https://x.com/replacement", "", 200, true))
        assertTrue(store.remove(first, SocialNetwork.X))
    }
}
