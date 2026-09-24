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
}
