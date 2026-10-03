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
            assertTrue(source.contains("IncomingMmsPrivateStore.persist(context.filesDir, data)"))
            assertFalse(source.contains("FileOutputStream("))
            assertFalse(source.contains("MessageDigest.getInstance("))
        }
    }

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
