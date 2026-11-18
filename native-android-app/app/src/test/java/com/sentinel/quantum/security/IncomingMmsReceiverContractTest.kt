package com.sentinel.quantum.security

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingMmsReceiverContractTest {
    @Test
    fun bothIngressReceiversUseTheSharedPrivatePersistenceBoundary() {
        val sourceRoot = locateSourceRoot()
        val deliver = File(
            sourceRoot,
            "com/sentinel/quantum/security/SentinelMmsDeliverReceiver.kt"
        ).readText()
        val download = File(
            sourceRoot,
            "com/sentinel/quantum/security/SentinelMmsDownloadReceiver.kt"
        ).readText()

        for (source in listOf(deliver, download)) {
            assertTrue(
                source.contains("IncomingMmsPrivateStore.persist(") ||
                    source.contains("IncomingMmsPrivateStore.confirmDurableCopy(")
            )
            assertTrue(source.contains("context.filesDir"))
            assertTrue(source.contains("data"))
            assertFalse(source.contains("FileOutputStream("))
            assertFalse(source.contains("MessageDigest.getInstance("))
        }
    }

    @Test
    fun downloadResultIsCheckedBeforeReadingAnyPayload() {
        val source = readSecuritySource("SentinelMmsDownloadReceiver.kt")
        val resultCheck = source.indexOf("if (deliveredResultCode != Activity.RESULT_OK)")
        val payloadRead = listOf("target.readBytes()", "IncomingMmsPduFileReader.read(")
            .map { source.indexOf(it) }
            .filter { it >= 0 }
            .minOrNull() ?: error("Missing staged PDU read")
        assertTrue("Transport failure must be handled before any payload read", resultCheck in 0 until payloadRead)
    }

    @Test
    fun callbackAndRecoveryDoNotReadFilesWithoutAnInReadBound() {
        for (name in listOf(
            "SentinelMmsDownloadReceiver.kt",
            "MmsDownloadRecoveryWorker.kt",
            "IncomingMmsPrivateStore.kt"
        )) {
            assertFalse("Unbounded PDU read in $name", readSecuritySource(name).contains(".readBytes()"))
        }
    }

    @Test
    fun opportunisticPruningHonorsTheRecoveryRetentionDecision() {
        val source = readSecuritySource("MmsDownloadCoordinator.kt")
        val prune = source.substringAfter("private fun prune(").substringBefore("private fun holdsSmsRole(")
        assertTrue("Prune must preserve retry and diagnosis records", prune.contains("cleanupMustKeepStaging(outcome)"))
    }

    private fun readSecuritySource(name: String): String =
        File(locateSourceRoot(), "com/sentinel/quantum/security/$name").readText()

    private fun locateSourceRoot(): File {
        val candidates = listOf(
            File("src/main/java"),
            File("app/src/main/java"),
            File("native-android-app/app/src/main/java")
        )
        return candidates.firstOrNull { root ->
            File(
                root,
                "com/sentinel/quantum/security/SentinelMmsDeliverReceiver.kt"
            ).isFile
        } ?: error("Unable to locate Android main source root from ${File(".").absolutePath}")
    }
}
