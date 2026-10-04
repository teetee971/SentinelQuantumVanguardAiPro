package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidPhoneNumberCanonicalizerPolicyTest {
    @Test fun explicitRegionWinsOverObservedRegions() {
        assertEquals(
            "GP",
            AndroidPhoneNumberCanonicalizer.resolveRegionIso(
                explicitRegionIso = "gp",
                simRegionIso = "fr",
                networkRegionIso = "mq"
            )
        )
    }

    @Test fun explicitInternationalOutputMustBeRealE164() {
        assertEquals(
            "+590690123456",
            AndroidPhoneNumberCanonicalizer.normalizeWithKnownRegion("+590 690 12 34 56", null)
        )
        assertNull(AndroidPhoneNumberCanonicalizer.normalizeWithKnownRegion("+01234567", null))
        assertNull(AndroidPhoneNumberCanonicalizer.normalizeWithKnownRegion("+0033612345678", null))
    }

    @Test fun overseasPlatformFallbacksRemainRegionScoped() {
        assertEquals(
            "+262639123456",
            AndroidPhoneNumberCanonicalizer.normalizeWithKnownRegion("0639 12 34 56", "YT")
        )
        assertEquals(
            "+508123456",
            AndroidPhoneNumberCanonicalizer.normalizeWithKnownRegion("0508 12 34 56", "PM")
        )
        assertEquals(
            "0508123456",
            AndroidPhoneNumberCanonicalizer.normalizeWithKnownRegion("0508 12 34 56", null)
        )
    }

    @Test fun identicalSimAndNetworkRegionsAreSafeForOneSubscription() {
        assertEquals(
            "GP",
            AndroidPhoneNumberCanonicalizer.resolveRegionIso(
                explicitRegionIso = null,
                simRegionIso = "gp",
                networkRegionIso = "GP"
            )
        )
    }

    @Test fun conflictingSimAndNetworkRegionsRemainUnknown() {
        assertNull(
            AndroidPhoneNumberCanonicalizer.resolveRegionIso(
                explicitRegionIso = null,
                simRegionIso = "gp",
                networkRegionIso = "fr"
            )
        )
    }

    @Test fun networkRegionIsUsedOnlyWhenKnownSubscriptionHasNoSimRegion() {
        assertEquals(
            "MQ",
            AndroidPhoneNumberCanonicalizer.resolveRegionIso(
                explicitRegionIso = null,
                simRegionIso = "",
                networkRegionIso = "mq"
            )
        )
    }

    @Test fun simRegionIsUsedWhenNetworkRegionIsUnavailable() {
        assertEquals(
            "GP",
            AndroidPhoneNumberCanonicalizer.resolveRegionIso(
                explicitRegionIso = null,
                simRegionIso = "gp",
                networkRegionIso = ""
            )
        )
    }

    @Test fun missingOrMalformedRegionNeverInventsFrance() {
        assertNull(
            AndroidPhoneNumberCanonicalizer.resolveRegionIso(
                explicitRegionIso = null,
                simRegionIso = "unknown",
                networkRegionIso = "590"
            )
        )
    }

    @Test fun identicalMultiSimRegionsAreSafeToUse() {
        assertEquals(
            "GP",
            AndroidPhoneNumberCanonicalizer.unambiguousRegion(listOf("gp", "GP"))
        )
    }

    @Test fun conflictingMultiSimRegionsRemainUnknown() {
        assertNull(
            AndroidPhoneNumberCanonicalizer.unambiguousRegion(listOf("GP", "FR"))
        )
    }

    @Test fun oneUnknownMultiSimRegionMakesWholeContextUnknown() {
        assertNull(
            AndroidPhoneNumberCanonicalizer.unambiguousRegion(listOf("GP", null))
        )
    }

    @Test fun malformedObservationMakesWholeMultiSimContextUnknown() {
        assertNull(
            AndroidPhoneNumberCanonicalizer.unambiguousRegion(listOf("GP", "590"))
        )
    }

    @Test fun noUsableActiveSubscriptionRegionRemainsUnknown() {
        assertNull(AndroidPhoneNumberCanonicalizer.unambiguousRegion(listOf("unknown", "590")))
    }
}
