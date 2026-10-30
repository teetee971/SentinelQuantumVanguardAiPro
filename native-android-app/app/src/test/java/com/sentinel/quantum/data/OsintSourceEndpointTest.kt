package com.sentinel.quantum.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class OsintSourceEndpointTest {
    @Test fun sourcesUseCanonicalCertFrOriginWithoutFollowingLegacyRedirects() {
        assertEquals(3, OsintSource.entries.size)
        OsintSource.entries.forEach {
            val uri = URI(it.url)
            assertEquals("https", uri.scheme)
            assertEquals("cert.cyber.gouv.fr", uri.host)
            assertTrue(uri.path.endsWith("/feed/"))
        }
    }
    @Test fun eachSourceStillRepresentsItsOriginalFeed() {
        assertEquals(listOf("/alerte/feed/", "/avis/feed/", "/cti/feed/"), OsintSource.entries.map { URI(it.url).path })
    }
}
