package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MmsProviderJournalMigrationTest {
    @Test
    fun legacyReady_isConservativelyDecodedAsSubmissionUnknown() {
        assertEquals(
            MmsProviderJournal.Phase.SUBMISSION_UNKNOWN,
            MmsProviderJournal.normalizePhaseForSchema(
                MmsProviderJournal.LEGACY_SCHEMA_VERSION,
                MmsProviderJournal.Phase.READY
            )
        )
    }

    @Test
    fun currentReady_remainsProvenPreTransport() {
        assertEquals(
            MmsProviderJournal.Phase.READY,
            MmsProviderJournal.normalizePhaseForSchema(
                MmsProviderJournal.CURRENT_SCHEMA_VERSION,
                MmsProviderJournal.Phase.READY
            )
        )
    }

    @Test
    fun legacyNonReadyPhase_preservesItsOriginalMeaning() {
        assertEquals(
            MmsProviderJournal.Phase.SUBMITTED,
            MmsProviderJournal.normalizePhaseForSchema(
                MmsProviderJournal.LEGACY_SCHEMA_VERSION,
                MmsProviderJournal.Phase.SUBMITTED
            )
        )
    }

    @Test
    fun unknownSchema_failsClosed() {
        assertNull(
            MmsProviderJournal.normalizePhaseForSchema(
                99,
                MmsProviderJournal.Phase.READY
            )
        )
    }
}
