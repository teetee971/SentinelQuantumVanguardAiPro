package com.sentinel.quantum.security

import org.junit.Assert.*
import org.junit.Test

class SentinelCleanupReportTest {
    private fun candidate()=SentinelCleanupPolicy.Candidate("/private/path",SentinelCleanupPolicy.Scope.SENTINEL_CACHE,"copy\nFAUX SUCCES.txt",123,SentinelCleanupPolicy.ActionState.EXECUTABLE,123)
    @Test fun onlyVerifiedDeletionIsReportedAsFreedAndPrivatePathIsOmitted() {
        val c=candidate();val result=SentinelCleanupPolicy.Result(c,c.copy(bytes=0,state=SentinelCleanupPolicy.ActionState.VERIFIED_REMOVED),SentinelCleanupPolicy.ExecutionEffect.REMOVED_BY_EXECUTION)
        val text=SentinelCleanupReport.render(listOf(result))
        assertTrue(text.contains("Volume logique supprimé : 123"));assertTrue(text.contains("Suppressions vérifiées : 1"));assertFalse(text.contains(c.stableId));assertFalse(text.contains("copy\n"))
    }
    @Test fun alreadyAbsentAndFailedResultsNeverBecomeFreedBytes() {
        val c=candidate();val text=SentinelCleanupReport.render(listOf(
            SentinelCleanupPolicy.Result(c,c.copy(bytes=0,state=SentinelCleanupPolicy.ActionState.VERIFIED_REMOVED),SentinelCleanupPolicy.ExecutionEffect.ALREADY_ABSENT),
            SentinelCleanupPolicy.Result(c,c.copy(state=SentinelCleanupPolicy.ActionState.FAILED))))
        assertTrue(text.contains("Volume logique supprimé : 0"));assertTrue(text.contains("Échecs : 1"));assertTrue(text.contains("Déjà absents : 1"))
    }
    @Test fun reportDoesNotImplyWholePhoneWasCleaned() {
        val text=SentinelCleanupReport.render(emptyList());assertTrue(text.contains("Copies temporaires Sentinel uniquement"));assertTrue(text.contains("Demandés : 0"))
    }
}
