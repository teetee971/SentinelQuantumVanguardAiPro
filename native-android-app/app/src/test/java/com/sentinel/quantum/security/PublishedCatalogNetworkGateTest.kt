package com.sentinel.quantum.security

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class PublishedCatalogNetworkGateTest {
    @Test fun lookupDoesNotOpenTheNetworkWhenDownloadIsDisabled() {
        val calls = AtomicInteger()
        val client = ArcepDirectoryClient(clientThatCounts(calls))
        try {
            client.lookup("0612345678") { false }
            throw AssertionError("missing local copy must not look like a match")
        } catch (disabled: CatalogNetworkDisabled) {
            assertEquals("CATALOG_NETWORK_DISABLED", disabled.message)
        }
        assertEquals(0, calls.get())
    }

    @Test fun refreshDoesNotOpenTheNetworkWhenDownloadIsDisabled() {
        val calls = AtomicInteger()
        assertFalse(ArcepDirectoryClient(clientThatCounts(calls)).refreshFromNetwork { false })
        assertFalse(RtrDirectoryClient(clientThatCounts(calls)).refreshFromNetwork { false })
        assertEquals(0, calls.get())
    }

    @Test fun revocationDuringTransferDiscardsTheBody() {
        val permitted = AtomicBoolean(true)
        val calls = AtomicInteger()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            calls.incrementAndGet()
            permitted.set(false)
            jsonResponse(chain.request(), """{"schemaVersion":2,"entries":[]}""")
        }.build()
        val client = ArcepDirectoryClient(http)
        assertFalse(client.refreshFromNetwork { permitted.get() })
        assertEquals(1, calls.get())
        try {
            client.lookup("0612345678") { false }
            throw AssertionError("revoked transfer must not become a local catalog")
        } catch (_: CatalogNetworkDisabled) {
            assertEquals(1, calls.get())
        }
    }

    @Test fun acceptedCopyStaysUsableAfterTheSwitchIsTurnedOff() {
        val calls = AtomicInteger()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            calls.incrementAndGet()
            jsonResponse(
                chain.request(),
                """{"schemaVersion":2,"entries":[["0600000000","0699999999","OP","FR","2020-01-01"]],"operators":{"OP":["Opérateur test"]}}"""
            )
        }.build()
        val client = ArcepDirectoryClient(http)
        client.refreshFromNetwork { true }
        val offline = client.lookup("0612345678") { false }
        assertEquals("OP", offline?.operatorCode)
        assertEquals("Opérateur test", offline?.attributedOperator)
        assertEquals(1, calls.get())
    }

    @Test fun httpFailureAndInvalidBodyAreNotStored() {
        val errors = AtomicInteger()
        val failing = ArcepDirectoryClient(OkHttpClient.Builder().addInterceptor { chain ->
            errors.incrementAndGet()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(503)
                .message("unavailable")
                .body("".toResponseBody("text/plain".toMediaType()))
                .build()
        }.build())
        assertFalse(failing.refreshFromNetwork { true })
        assertLookupBlocked(failing)
        assertEquals(1, errors.get())

        val invalidCalls = AtomicInteger()
        val invalid = ArcepDirectoryClient(OkHttpClient.Builder().addInterceptor { chain ->
            invalidCalls.incrementAndGet()
            jsonResponse(chain.request(), "not-a-catalog")
        }.build())
        assertFalse(invalid.refreshFromNetwork { true })
        assertLookupBlocked(invalid)
        assertEquals(1, invalidCalls.get())
    }

    @Test fun timeoutDoesNotCreateALocalCatalog() {
        val slow = ArcepDirectoryClient(
            OkHttpClient.Builder()
                .callTimeout(200, TimeUnit.MILLISECONDS)
                .addInterceptor {
                    Thread.sleep(1_000)
                    throw java.io.IOException("late")
                }
                .build()
        )
        assertFalse(slow.refreshFromNetwork { true })
        assertLookupBlocked(slow)
    }

    @Test fun concurrentLookupsShareOneAuthorizedDownload() {
        val calls = AtomicInteger()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            calls.incrementAndGet()
            jsonResponse(
                chain.request(),
                """{"schemaVersion":2,"entries":[["0600000000","0699999999","OP","FR","2020-01-01"]],"operators":{"OP":["Opérateur test"]}}"""
            )
        }.build()
        val client = ArcepDirectoryClient(http)
        val results = arrayOfNulls<ArcepDirectoryClient.Allocation>(2)
        val threads = List(2) { index ->
            Thread {
                results[index] = client.lookup("0612345678") { true }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join(3_000) }
        assertEquals("OP", results[0]?.operatorCode)
        assertEquals("OP", results[1]?.operatorCode)
        assertEquals(1, calls.get())
    }

    @Test fun disabledSearchThenReenabledSearchIssuesOneGet() {
        val calls = AtomicInteger()
        val client = ArcepDirectoryClient(clientThatCounts(calls))
        assertFalse(client.refreshFromNetwork { false })
        assertLookupBlocked(client)
        client.refreshFromNetwork { true }
        val found = client.lookup("0612345678") { false }
        assertNull(found)
        assertEquals(1, calls.get())
    }

    @Test fun screeningServiceDoesNotReferenceCatalogClients() {
        val source = java.io.File(
            "src/main/java/com/sentinel/quantum/security/SentinelCallScreeningService.kt"
        ).readText()
        assertFalse(source.contains("ArcepDirectoryClient"))
        assertFalse(source.contains("RtrDirectoryClient"))
        assertFalse(source.contains("refreshFromNetwork"))
        assertFalse(source.contains("CatalogNetworkDisabled"))
    }

    private fun assertLookupBlocked(client: ArcepDirectoryClient) {
        try {
            client.lookup("0612345678") { false }
            throw AssertionError("failed download must not become a usable catalog")
        } catch (_: CatalogNetworkDisabled) {
        }
    }

    @Test fun callerCardAndDialerPassTheLiveSwitchIntoLookup() {
        val caller = java.io.File("src/main/java/com/sentinel/quantum/CallerIdActivity.kt").readText()
        val dialer = java.io.File("src/main/java/com/sentinel/quantum/SentinelDialerActivity.kt").readText()
        val security = java.io.File(
            "src/main/java/com/sentinel/quantum/ui/screens/PhoneSecurityScreen.kt"
        ).readText()
        val worker = java.io.File(
            "src/main/java/com/sentinel/quantum/background/PublishedNumberCatalogWorker.kt"
        ).readText()
        listOf(caller, dialer, security).forEach { source ->
            assertTrue(source.contains("isPublishedCatalogRefreshEnabled()"))
            assertTrue(source.contains("CatalogNetworkDisabled"))
        }
        assertTrue(worker.contains("refreshFromNetwork(networkPermitted)"))
        assertFalse(worker.contains("refreshFromNetwork()"))
    }

    private fun clientThatCounts(calls: AtomicInteger): OkHttpClient =
        OkHttpClient.Builder().addInterceptor { chain ->
            calls.incrementAndGet()
            jsonResponse(chain.request(), """{"schemaVersion":2,"entries":[]}""")
        }.build()

    private fun jsonResponse(request: okhttp3.Request, json: String): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(json.toResponseBody("application/json".toMediaType()))
            .build()
}
