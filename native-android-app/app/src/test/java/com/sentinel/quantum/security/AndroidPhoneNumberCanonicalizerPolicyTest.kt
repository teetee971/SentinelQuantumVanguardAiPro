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

    @Test fun selectedSimRegionWinsOverNetworkRegionWhenNoExplicitChoiceExists() {
        assertEquals(
            "GP",
            AndroidPhoneNumberCanonicalizer.resolveRegionIso(
                explicitRegionIso = null,
                simRegionIso = "gp",
                networkRegionIso = "fr"
            )
        )
    }

    @Test fun networkRegionIsUsedOnlyWhenHigherPrioritySourcesAreMissing() {
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
}
