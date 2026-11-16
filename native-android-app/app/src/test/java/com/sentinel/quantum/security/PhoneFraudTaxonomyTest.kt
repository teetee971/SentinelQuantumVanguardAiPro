package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneFraudTaxonomyTest {
    @Test fun mapsKnownSignalsWithoutInventingUnknownCategories() {
        assertEquals(PhoneFraudCategory.WANGIRI, PhoneFraudTaxonomy.fromSignal("short-ring"))
        assertEquals(PhoneFraudCategory.BANK_IMPERSONATION, PhoneFraudTaxonomy.fromSignal("fake bank"))
        assertEquals(PhoneFraudCategory.PHISHING_LINK, PhoneFraudTaxonomy.fromSignal("malicious link"))
        assertNull(PhoneFraudTaxonomy.fromSignal("UNRECOGNISED_FUTURE_SIGNAL"))
    }

    @Test fun mapsSupportedCallCategoriesToTheModeratedWireContract() {
        assertEquals(
            CommunityReportClient.Category.SPOOFING,
            PhoneFraudTaxonomy.toCommunityCategory(PhoneFraudCategory.SPOOFING)
        )
        assertEquals(
            CommunityReportClient.Category.BANK_IMPERSONATION,
            PhoneFraudTaxonomy.toCommunityCategory(PhoneFraudCategory.BANK_IMPERSONATION)
        )
        assertEquals(
            CommunityReportClient.Category.TELEMARKETING,
            PhoneFraudTaxonomy.toCommunityCategory(PhoneFraudCategory.TELEMARKETING)
        )
        assertEquals(
            CommunityReportClient.Category.OTHER,
            PhoneFraudTaxonomy.toCommunityCategory(PhoneFraudCategory.PHISHING_LINK)
        )
    }
}
