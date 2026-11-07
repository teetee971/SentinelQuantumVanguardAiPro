package com.sentinel.quantum.security

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SentinelOwnedCleanupSafetyTest {
    @get:Rule val temporary = TemporaryFolder()
    private val now = 1_800_000_000_000L
    private val old = now - 2 * 86_400_000L
    private fun root() = temporary.newFolder().canonicalFile
    private fun file(root: File, name: String, modified: Long = old): File = File(root, name).apply {
        parentFile!!.mkdirs(); writeText("temporary copy"); assertTrue(setLastModified(modified))
    }
    private fun candidate(f: File) = SentinelCleanupPolicy.Candidate(f.absolutePath, SentinelCleanupPolicy.Scope.SENTINEL_CACHE,
        f.name, f.length(), SentinelCleanupPolicy.ActionState.EXECUTABLE, f.lastModified())
    @Test fun activeMmsIsNeitherDiscoveredNorDeleted() {
        val r=root();val f=file(r,"sentinel_mms_send/active.pdu")
        assertTrue(SentinelOwnedCleanupCollector(listOf(r)){now}.discover().isEmpty())
        assertFalse(SentinelOwnedCleanupExecutor(listOf(r)){now}.execute(candidate(f)).isVerifiedCleaned)
        assertTrue(f.exists())
    }
    @Test fun onlyOldRecognizedTemporaryCopiesAreDiscoverable() {
        val r=root();val allowed=file(r,"sentinel_log_export/old.txt")
        file(r,"sentinel_sms_export/recent.txt",now);file(r,"sentinel_sms_export/future.txt",now+1)
        file(r,"unknown.bin");file(r,"voice-studio-preview.m4a");file(r,"sentinel_mms_download/payload.pdu")
        assertEquals(listOf(allowed.absolutePath),SentinelOwnedCleanupCollector(listOf(r)){now}.discover().map{it.stableId})
    }
    @Test fun forgedFreshOrUnknownCandidateIsRejectedByExecutor() {
        val r=root();val e=SentinelOwnedCleanupExecutor(listOf(r)){now}
        for(f in listOf(file(r,"unknown.tmp"),file(r,"sentinel_sms_export/recent.txt",now))) {
            assertFalse(e.execute(candidate(f)).isVerifiedCleaned);assertTrue(f.exists())
        }
    }
    @Test fun actualOldCopyDeletionIsVerifiedAndMeasured() {
        val r=root();val f=file(r,"sentinel_sms_export/old.txt");val c=candidate(f)
        val result=SentinelOwnedCleanupExecutor(listOf(r)){now}.execute(c)
        assertTrue(result.isVerifiedCleaned);assertEquals(c.bytes,result.verifiedFreedBytes);assertFalse(f.exists())
    }
    @Test fun modifiedAfterScanIsNotDeleted() {
        val r=root();val f=file(r,"sentinel_log_export/old.txt");val c=candidate(f)
        f.appendText("changed");assertFalse(SentinelOwnedCleanupExecutor(listOf(r)){now}.execute(c).isVerifiedCleaned);assertTrue(f.exists())
    }
    @Test fun symbolicLinkToOldCopyIsNotADeletionCandidate() {
        val r=root();val f=file(r,"sentinel_log_export/original.txt");val link=File(r,"sentinel_log_export/link.txt")
        Files.createSymbolicLink(link.toPath(),f.toPath())
        assertEquals(listOf(f.absolutePath),SentinelOwnedCleanupCollector(listOf(r)){now}.discover().map{it.stableId})
        assertFalse(SentinelOwnedCleanupExecutor(listOf(r)){now}.execute(candidate(link)).isVerifiedCleaned);assertTrue(f.exists())
    }
    @Test fun staleApkInspectionCopyCanBeRemovedButNotOtherApk() {
        val r=root();val f=file(r,"sentinel_apk_inspection_old.apk");file(r,"download.apk")
        assertEquals(listOf(f.absolutePath),SentinelOwnedCleanupCollector(listOf(r)){now}.discover().map{it.stableId})
    }

    @Test fun allowedContextRootAliasIsCanonicalizedBeforeScan() {
        val r=root();val f=file(r,"sentinel_log_export/old.txt");val alias=File(temporary.root,"cache-alias")
        Files.createSymbolicLink(alias.toPath(),r.toPath())
        assertEquals(listOf(f.absolutePath),SentinelOwnedCleanupCollector(listOf(alias)){now}.discover().map{it.stableId})
    }
    @Test fun inaccessibleRootIsAnExplicitPartialScan() {
        val inaccessible=object:File("/unreadable-cache") { override fun exists():Boolean=throw SecurityException("denied") }
        assertFalse(SentinelOwnedCleanupCollector(listOf(inaccessible)){now}.scan().coverageComplete)
    }
    @Test fun scanLimitIsExplicitAndDoesNotModifyFiles() {
        val r=root();repeat(6){file(r,"sentinel_log_export/$it.txt")}
        val scan=SentinelOwnedCleanupCollector(listOf(r)){now}.scan(maxEntries=3)
        assertFalse(scan.coverageComplete);assertTrue(scan.limitReached);assertTrue(scan.candidates.size<=3)
        assertEquals(6,File(r,"sentinel_log_export").listFiles()!!.size)
    }

    @Test fun canonicalizationFailureInsideEligibleLocationMakesCoveragePartial() {
        val r=root();val existing=file(r,"sentinel_log_export/old.txt")
        val unreadable=object:File(existing.path) { override fun getCanonicalFile():File=throw java.io.IOException("metadata unavailable") }
        val unreadableEntries = SentinelCleanupDirectoryEntries { _, visitor -> visitor(unreadable); Unit }
        assertFalse(SentinelOwnedCleanupCollector(listOf(r), unreadableEntries) { now }.scan().coverageComplete)
        assertTrue(existing.exists())
    }
    @Test fun scannerNeverMaterializesAnEntireDirectoryListing() {
        val r = root()
        val oldCopy = file(r, "sentinel_apk_inspection_old.apk")
        val streamingOnly = object : File(r.path) {
            override fun getCanonicalFile(): File = this
            override fun listFiles(): Array<File> = error("Unbounded listing is forbidden")
        }
        val scan = SentinelOwnedCleanupCollector(listOf(streamingOnly)) { now }.scan()
        assertTrue("Directory must be consumed as a stream", scan.coverageComplete)
        assertEquals(listOf(oldCopy.absolutePath), scan.candidates.map { it.stableId })
        assertTrue(oldCopy.exists())
    }
    @Test fun unavailableStreamingIsPartialAndNeverDeletes() {
        val r = root(); val oldCopy = file(r, "sentinel_log_export/old.txt")
        val scan = SentinelOwnedCleanupCollector(listOf(r), SentinelCleanupDirectoryEntries.forAndroid(24)) { now }.scan()
        assertFalse(scan.coverageComplete); assertTrue(scan.candidates.isEmpty()); assertTrue(oldCopy.exists())
    }
    @Test fun enumerationFailureIsPartialAndNeverDeletes() {
        val r = root(); val oldCopy = file(r, "sentinel_log_export/old.txt")
        val failing = SentinelCleanupDirectoryEntries { _, _ -> throw java.io.IOException("unreadable") }
        val scan = SentinelOwnedCleanupCollector(listOf(r), failing) { now }.scan()
        assertFalse(scan.coverageComplete); assertTrue(scan.candidates.isEmpty()); assertTrue(oldCopy.exists())
    }
}
