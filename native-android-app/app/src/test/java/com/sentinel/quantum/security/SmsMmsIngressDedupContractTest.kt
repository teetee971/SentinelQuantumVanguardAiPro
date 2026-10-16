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
        val method = source.indexOf("private fun hasExistingIncomingSms")
        val methodEnd = source.indexOf("private fun holdsSmsRole", startIndex = method)
        val query = source.indexOf("context.contentResolver.query(", startIndex = method)
        val nullCursorFailsClosed = source.indexOf("?: true", startIndex = query)

        assertTrue(method >= 0)
        assertTrue(methodEnd > method)
        assertTrue(query in method until methodEnd)
        assertTrue(nullCursorFailsClosed in query until methodEnd)
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
        val cleanupBypass = source.indexOf("!allowQuarantine &&", startIndex = recordRead)
        val recoverySafeExpiry = source.indexOf("MmsDownloadCoordinator.expireFromRecovery", startIndex = expiryCheck)

        assertTrue(recordRead >= 0)
        assertTrue(cleanupBypass > recordRead)
        assertTrue(expiryCheck > cleanupBypass)
        assertTrue(recoverySafeExpiry > expiryCheck)
        assertTrue(stagedFileRead > recoverySafeExpiry)
    }

    @Test fun recoveryExpiryDoesNotCancelTheRunningRecoveryWorker() {
        val source = sourceFile("MmsDownloadCoordinator.kt").readText()
        val method = source.indexOf("internal fun expireFromRecovery")
        val cancelCleanup = source.indexOf("cancelCleanup = true", startIndex = method)
        val keepRecovery = source.indexOf("cancelRecovery = false", startIndex = method)
        val release = source.indexOf("releaseNotificationDedup(context, fileName)", startIndex = method)

        assertTrue(method >= 0)
        assertTrue(cancelCleanup > method)
        assertTrue(keepRecovery > cancelCleanup)
        assertTrue(release > keepRecovery)
    }

    @Test fun recoveryJournalEnumerationFailsClosedWhenSanitationCannotCommit() {
        val source = sourceFile("MmsDownloadRecoveryJournal.kt").readText()
        val allMethod = source.indexOf("fun all(): List<Record> = synchronized(LOCK) {")
        val nextMethod = source.indexOf("private fun sanitizeInvalidEntries", startIndex = allMethod)
        val failClosed = source.indexOf("if (!sanitizeInvalidEntries()) return@synchronized emptyList()", startIndex = allMethod)
        val enumeration = source.indexOf("preferences.all.asSequence()", startIndex = allMethod)

        assertTrue(allMethod >= 0)
        assertTrue(nextMethod > allMethod)
        assertTrue(failClosed in allMethod until nextMethod)
        assertTrue(enumeration in (failClosed + 1) until nextMethod)
    }

    @Test fun recoveryJournalCapacityUsesProcessWideLock() {
        val source = sourceFile("MmsDownloadRecoveryJournal.kt").readText()
        val sharedLock = source.indexOf("private val LOCK = Any()")
        val recordMethod = source.indexOf("fun record(")
        val nextMethod = source.indexOf("fun read(", startIndex = recordMethod)
        val recordLock = source.indexOf("synchronized(LOCK)", startIndex = recordMethod)

        assertTrue(sharedLock >= 0)
        assertTrue(recordMethod >= 0)
        assertTrue(nextMethod > recordMethod)
        assertTrue(recordLock in recordMethod until nextMethod)
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
