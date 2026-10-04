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

    @Test fun simRegionWinsOverNetworkRegionForOneKnownSubscription() {
        assertEquals(
            "GP",
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
