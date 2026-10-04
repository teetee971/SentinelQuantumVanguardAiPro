package com.sentinel.quantum.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidPhoneNumberCanonicalizerInstrumentationTest {
    @Test
    fun nationalNumbersMapToExpectedE164ForSupportedFrenchRegions() {
        val cases = listOf(
            Triple("06 12 34 56 78", "FR", "+33612345678"),
            Triple("0690 12 34 56", "GP", "+590690123456"),
            Triple("0696 12 34 56", "MQ", "+596696123456"),
            Triple("0694 12 34 56", "GF", "+594694123456"),
            Triple("0692 12 34 56", "RE", "+262692123456"),
            Triple("0639 12 34 56", "YT", "+262639123456")
        )

        cases.forEach { (raw, region, expected) ->
            assertEquals(
                "$region must map its national representation to the expected E.164 identity",
                expected,
                AndroidPhoneNumberCanonicalizer.normalizeWithKnownRegion(raw, region)
            )
        }
    }

    @Test
    fun explicitInternationalFormsRemainStableRegardlessOfRegionContext() {
        assertEquals(
            "+590690123456",
            AndroidPhoneNumberCanonicalizer.normalizeWithKnownRegion("+590 690 12 34 56", "FR")
        )
        assertEquals(
            "+590690123456",
            AndroidPhoneNumberCanonicalizer.normalizeWithKnownRegion("00 590 690 12 34 56", "FR")
        )
    }

    @Test
    fun missingRegionNeverInventsCountryForNationalSyntax() {
        assertEquals(
            "0690123456",
            AndroidPhoneNumberCanonicalizer.normalizeWithKnownRegion("0690 12 34 56", null)
        )
        assertEquals(
            "0690123456",
            AndroidPhoneNumberCanonicalizer.normalizeWithKnownRegion("0690 12 34 56", "")
        )
    }

    @Test
    fun malformedInputStillFailsClosed() {
        assertNull(AndroidPhoneNumberCanonicalizer.normalizeWithKnownRegion("not-a-number", "GP"))
    }
}
