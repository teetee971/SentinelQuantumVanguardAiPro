package com.sentinel.quantum.security

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsMmsIngressDedupContractTest {
    @Test fun smsReceiverDeduplicatesBeforeProviderInsert() {
        val source = sourceFile("SentinelSmsDeliverReceiver.kt").readText()
        val identity = source.indexOf("SmsIngressIdentity.from")
        val duplicateCheck = source.indexOf("hasExistingIncomingSms")
        val insert = source.indexOf("contentResolver.insert")

        assertTrue(identity >= 0)
        assertTrue(duplicateCheck > identity)
        assertTrue(insert > duplicateCheck)
    }

    @Test fun smsReplayCheckFailsClosedWhenProviderReturnsNullCursor() {
        val source = sourceFile("SentinelSmsDeliverReceiver.kt").readText()
        val query = source.indexOf("context.contentResolver.query(")
        val insert = source.indexOf("contentResolver.insert")
        val nullCursorFailsClosed = source.indexOf("?: true", startIndex = query)

        assertTrue(query >= 0)
        assertTrue(insert > query)
        assertTrue(nullCursorFailsClosed in query until insert)
    }

    @Test fun mmsNotificationDeduplicatesBeforeStagingFileCreation() {
        val source = sourceFile("MmsDownloadCoordinator.kt").readText()
        val reserve = source.indexOf("notificationDedup.reserve")
        val createFile = source.indexOf("canonicalFile.createNewFile")

        assertTrue(reserve >= 0)
        assertTrue(createFile > reserve)
    }

    @Test fun expiredMmsRecoveryTerminatesBeforeStagedPayloadAccess() {
        val source = sourceFile("MmsDownloadRecoveryWorker.kt").readText()
        val recordRead = source.indexOf("val record = journal.read(fileName)")
        val expiryCheck = source.indexOf("MmsDownloadRecoveryJournal.isExpired", startIndex = recordRead)
        val stagedFileRead = source.indexOf("stagedFile(appContext, fileName)", startIndex = recordRead)

        assertTrue(recordRead >= 0)
        assertTrue(expiryCheck > recordRead)
        assertTrue(stagedFileRead > expiryCheck)
    }

    private fun sourceFile(name: String): File {
        val candidates = listOf(
            File("src/main/java/com/sentinel/quantum/security/$name"),
            File("app/src/main/java/com/sentinel/quantum/security/$name"),
            File("native-android-app/app/src/main/java/com/sentinel/quantum/security/$name")
        )
        return candidates.firstOrNull(File::isFile)
            ?: error("Unable to locate $name from ${File(".").absolutePath}")
    }
}
