package com.sentinel.quantum.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafScanCompletionPolicyTest {
    private fun complete(
        scanned: Int = 4999,
        maxDocuments: Int = 5000,
        pendingFolders: Int = 0,
        documentLimitReached: Boolean = false,
        depthLimited: Boolean = false,
        inaccessibleFolders: Int = 0
    ) = SafScanCompletionPolicy.isComplete(
        scanned, maxDocuments, pendingFolders,
        documentLimitReached, depthLimited, inaccessibleFolders
    )

    @Test fun finishedBelowLimitIsComplete() {
        assertTrue(complete())
    }

    @Test fun exactDocumentLimitIsIncomplete() {
        assertFalse(complete(scanned = 5000))
    }

    @Test fun pendingFoldersMakeScanIncomplete() {
        assertFalse(complete(pendingFolders = 1))
    }

    @Test fun documentLimitFlagMakesScanIncomplete() {
        assertFalse(complete(documentLimitReached = true))
    }

    @Test fun depthLimitMakesScanIncomplete() {
        assertFalse(complete(depthLimited = true))
    }

    @Test fun inaccessibleFolderMakesScanIncomplete() {
        assertFalse(complete(inaccessibleFolders = 1))
    }
}
