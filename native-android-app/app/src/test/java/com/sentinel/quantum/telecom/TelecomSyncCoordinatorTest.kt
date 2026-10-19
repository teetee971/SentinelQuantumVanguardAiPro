package com.sentinel.quantum.telecom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TelecomSyncCoordinatorTest {
    private val source = GlobalTelecomSourceRegistry.sources.first { it.id == "ARCEP" }

    @Test
    fun successfulOfficialProbeStoresReachabilityButNotNumberingData() {
        val store = InMemoryTelecomEvidenceStore(maxEntries = 4)
        val adapter = OfficialSourceProbeAdapter(
            descriptor = source,
            transport = OfficialSourceTransport { _, _ ->
                TransportResponse(
                    statusCode = 200,
                    contentType = "text/html; charset=utf-8",
                    body = "<html>official</html>".toByteArray()
                )
            }
        )

        val result = TelecomSourceSyncCoordinator(store).sync(adapter, nowMs = 1_000L)
        val cached = store.get("ARCEP", nowMs = 1_001L)

        assertEquals(TelecomSyncOutcome.REACHABLE_METADATA_ONLY, result.outcome)
        assertEquals(TelecomObservation.REACHABLE_METADATA_ONLY, cached?.observation)
        assertTrue(cached?.contentSha256?.length == 64)
        assertTrue(cached?.expiresAtMs ?: 0L > 1_000L)
        assertTrue(cached?.numberingRecords?.isEmpty() == true)
    }

    @Test
    fun invalidHttpResponseFailsOpenAndDoesNotReplaceLastGoodEvidence() {
        val store = InMemoryTelecomEvidenceStore(maxEntries = 4)
        val good = OfficialSourceProbeAdapter(source, OfficialSourceTransport { _, _ ->
            TransportResponse(200, "application/json", "{}".toByteArray())
        })
        val bad = OfficialSourceProbeAdapter(source, OfficialSourceTransport { _, _ ->
            TransportResponse(500, "text/plain", ByteArray(0))
        })

        TelecomSourceSyncCoordinator(store).sync(good, nowMs = 1_000L)
        val failed = TelecomSourceSyncCoordinator(store).sync(bad, nowMs = 2_000L)
        val cached = store.get("ARCEP", nowMs = 2_001L)

        assertEquals(TelecomSyncOutcome.UNAVAILABLE, failed.outcome)
        assertEquals(TelecomObservation.REACHABLE_METADATA_ONLY, cached?.observation)
        assertNull(failed.errorCode?.takeIf { it == "CALL_REJECT" })
    }

    @Test
    fun expiredEvidenceIsReportedAsStaleWithoutBeingReturnedAsFresh() {
        val store = InMemoryTelecomEvidenceStore(maxEntries = 4, defaultTtlMs = 10L)
        val adapter = OfficialSourceProbeAdapter(source, OfficialSourceTransport { _, _ ->
            TransportResponse(200, "text/html", "ok".toByteArray())
        })

        TelecomSourceSyncCoordinator(store, ttlMs = 10L).sync(adapter, nowMs = 100L)

        assertEquals(null, store.get("ARCEP", nowMs = 111L))
        assertEquals(TelecomObservation.STALE, store.lastKnown("ARCEP")?.observation)
    }

    @Test
    fun dashboardDoesNotClaimOfflineNumberingUntilAValidatedRecordExists() {
        val store = InMemoryTelecomEvidenceStore(maxEntries = 4)
        val repository = GlobalTelecomIntelligenceRepository(
            registry = listOf(source),
            store = store
        )
        assertEquals(false, repository.dashboard(nowMs = 1_000L).offlineNumberingAvailable)

        store.put(
            TelecomEvidenceRecord(
                sourceId = "ARCEP",
                observation = TelecomObservation.VALIDATED_DATASET,
                fetchedAtMs = 900L,
                expiresAtMs = 2_000L,
                contentSha256 = "a".repeat(64),
                contentType = "application/json",
                payloadBytes = 12,
                numberingRecords = listOf(
                    TelecomNumberingRecord("+33", "FR", "GEOGRAPHIC")
                )
            )
        )

        assertEquals(true, repository.dashboard(nowMs = 1_000L).offlineNumberingAvailable)
    }
}
