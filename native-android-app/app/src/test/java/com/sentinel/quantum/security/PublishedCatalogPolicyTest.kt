package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PublishedCatalogPolicyTest {
    @Test fun arcepDocumentIsAcceptedOnlyAsAnAllocationCatalog() {
        val document = """{"schemaVersion":2,"generatedAt":"2026-11-10","entries":[]}"""
        val decision = PublishedCatalogPolicy.evaluate(PublishedCatalogPolicy.Kind.ARCEP, document)
        assertTrue(decision.accepted)
        assertEquals("2026-11-10", decision.generatedAt)
    }

    @Test fun catalogCarryingASilenceInstructionIsRejected() {
        val document = """{"schemaVersion":2,"entries":[],"silence_prefix":"+336"}"""
        val decision = PublishedCatalogPolicy.evaluate(PublishedCatalogPolicy.Kind.ARCEP, document)
        assertFalse(decision.accepted)
        assertEquals("CATALOG_ACTION_FORBIDDEN", decision.reason)
    }

    @Test fun rtrRequiresAustriaAndGroups() {
        val accepted = PublishedCatalogPolicy.evaluate(
            PublishedCatalogPolicy.Kind.RTR,
            """{"schemaVersion":1,"country":"AT","groups":{}}"""
        )
        val rejected = PublishedCatalogPolicy.evaluate(
            PublishedCatalogPolicy.Kind.RTR,
            """{"schemaVersion":1,"country":"FR","groups":{}}"""
        )
        assertTrue(accepted.accepted)
        assertEquals("CATALOG_COUNTRY", rejected.reason)
    }

    @Test fun signedSpamFeedStaysUnprovisionedWithoutProductionKeys() {
        assertEquals(
            PublishedCatalogPolicy.SignedFeedPlan.NOT_PROVISIONED,
            PublishedCatalogPolicy.signedSpamFeedPlan(productionFeedReady = false, userEnabled = true)
        )
    }

    @Test fun freshnessWindowIsEightDays() {
        val now = PublishedCatalogPolicy.MAX_AGE_MS * 2
        assertTrue(PublishedCatalogPolicy.fresh(now - PublishedCatalogPolicy.MAX_AGE_MS, now))
        assertFalse(PublishedCatalogPolicy.fresh(now - PublishedCatalogPolicy.MAX_AGE_MS - 1, now))
    }
}
