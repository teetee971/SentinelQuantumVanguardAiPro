package com.sentinel.quantum.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneCoreCertificationProvenanceTest {
    private fun scope(
        installationId: String = "install-a",
        versionCode: Long = 2L,
        versionName: String = "2.0",
        lastUpdateTimeMs: Long = 1000L,
        sessionId: String = "session-a"
    ) = PhoneCoreCertificationProvenance.Scope(
        installationId, versionCode, versionName, lastUpdateTimeMs, sessionId
    )

    @Test fun exactCurrentScopeIsAccepted() {
        assertTrue(PhoneCoreCertificationProvenance.belongsTo(scope(), scope()))
    }

    @Test fun previousBuildCannotCertifyCurrentBuild() {
        assertFalse(
            PhoneCoreCertificationProvenance.belongsTo(
                scope(versionCode = 1L, versionName = "1.9", lastUpdateTimeMs = 900L),
                scope()
            )
        )
    }

    @Test fun previousSessionCannotCertifyNewCertificationSession() {
        assertFalse(
            PhoneCoreCertificationProvenance.belongsTo(
                scope(sessionId = "session-old"),
                scope(sessionId = "session-new")
            )
        )
    }

    @Test fun anotherInstallationCannotReuseProof() {
        assertFalse(
            PhoneCoreCertificationProvenance.belongsTo(
                scope(installationId = "install-old"),
                scope(installationId = "install-current")
            )
        )
    }

    @Test fun missingOrMalformedProvenanceFailsClosed() {
        assertFalse(PhoneCoreCertificationProvenance.belongsTo(null, scope()))
        assertFalse(
            PhoneCoreCertificationProvenance.belongsTo(
                scope(installationId = "raw phone +590690000000"),
                scope()
            )
        )
        assertNotNull(PhoneCoreCertificationProvenance.normalize(scope()))
    }
    @Test fun certificationRejectsLegacyEventWithoutProvenance() {
        val evidence = PhoneCorePhysicalValidation.evaluateCertification(
            events = listOf(
                PhonePrivateTimeline.Event(
                    PhonePrivateTimeline.Kind.CALL, 1_000L, "INCOMING",
                    PhoneCorePhysicalValidation.SIGNAL_CALL_ACTIVE
                )
            ),
            activeScope = scope()
        )
        assertFalse(evidence.incomingCallConnected)
    }

    @Test fun certificationAcceptsOnlyExactScope() {
        val active = scope()
        val matching = PhonePrivateTimeline.Event(
            PhonePrivateTimeline.Kind.CALL, 1_000L, "INCOMING",
            PhoneCorePhysicalValidation.SIGNAL_CALL_ACTIVE, active
        )
        val stale = matching.copy(provenance = scope(sessionId = "session-old"))
        val accepted = PhoneCorePhysicalValidation.evaluateCertification(listOf(matching), active)
        val rejected = PhoneCorePhysicalValidation.evaluateCertification(listOf(stale), active)
        assertTrue(accepted.incomingCallConnected)
        assertFalse(rejected.incomingCallConnected)
    }

    @Test fun missingActiveScopeFailsClosed() {
        val event = PhonePrivateTimeline.Event(
            PhonePrivateTimeline.Kind.CALL, 1_000L, "INCOMING",
            PhoneCorePhysicalValidation.SIGNAL_CALL_ACTIVE, scope()
        )
        assertFalse(
            PhoneCorePhysicalValidation.evaluateCertification(listOf(event), null).incomingCallConnected
        )
    }

}
