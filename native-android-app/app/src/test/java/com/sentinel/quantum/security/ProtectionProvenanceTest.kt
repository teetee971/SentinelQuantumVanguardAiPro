package com.sentinel.quantum.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectionProvenanceTest {
    @Test fun verifiedAssertionRequiresOfficialVerifiedEvidence() {
        assertTrue(ProtectionProvenance.canAssertVerifiedFact(
            ProtectionProvenance.Evidence(
                ProtectionProvenance.Source.OFFICIAL_DIRECTORY,
                "Attribution issue de la source officielle",
                ProtectionProvenance.Confidence.VERIFIED
            )
        ))
        assertFalse(ProtectionProvenance.canAssertVerifiedFact(
            ProtectionProvenance.Evidence(
                ProtectionProvenance.Source.COMMUNITY_REPORT,
                "Signalements utilisateurs",
                ProtectionProvenance.Confidence.CORROBORATED
            )
        ))
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankEvidenceLabelIsRejected() {
        ProtectionProvenance.Evidence(
            ProtectionProvenance.Source.SENTINEL_ANALYSIS,
            " ",
            ProtectionProvenance.Confidence.INDICATIVE
        )
    }
}
