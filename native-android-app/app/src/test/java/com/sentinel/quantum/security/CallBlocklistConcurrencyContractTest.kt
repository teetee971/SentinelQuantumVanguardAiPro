package com.sentinel.quantum.security

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class CallBlocklistConcurrencyContractTest {
    private val source = File(
        "src/main/java/com/sentinel/quantum/security/CallBlocklistStore.kt"
    ).readText()

    @Test
    fun every_persisted_mutation_and_snapshot_publication_uses_the_process_coordinator() {
        val methods = listOf(
            "setArcepVerifiedBlockingEnabled",
            "addBlockedNumber",
            "clearBlockedNumbers",
            "removeBlockedNumber",
            "purgeExpiredBlockedNumbers",
            "addBlockedPrefix",
            "removeBlockedPrefix",
            "replaceBlockedPrefixes",
            "installSignedSilenceRules",
            "prepareScreeningSnapshot"
        )
        methods.forEach { method ->
            val block = source.substringAfter("fun $method")
                .substringBefore("\n    fun ", missingDelimiterValue = "")
                .ifEmpty { source.substringAfter("fun $method") }
            assertTrue("$method must use the shared coordinator", block.contains("CallRuleMutationCoordinator.withLock"))
        }
        assertTrue("legacy install lock must not be separate", !source.contains("INSTALL_LOCK"))
    }

    @Test
    fun screening_cache_path_remains_memory_only_and_non_blocking() {
        val cached = source.substringAfter("internal fun cachedSnapshotForScreening")
        assertTrue(cached.contains("SCREENING_SNAPSHOT.activeAt(now)"))
        assertTrue(!cached.contains("preferences"))
        assertTrue(!cached.contains("withLock"))
    }
}
