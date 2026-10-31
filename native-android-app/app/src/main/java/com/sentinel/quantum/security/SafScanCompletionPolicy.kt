package com.sentinel.quantum.security

internal object SafScanCompletionPolicy {
    fun isComplete(
        scanned: Int,
        maxDocuments: Int,
        pendingFolders: Int,
        documentLimitReached: Boolean,
        depthLimited: Boolean,
        inaccessibleFolders: Int
    ): Boolean {
        require(maxDocuments > 0)
        require(scanned >= 0)
        require(pendingFolders >= 0)
        require(inaccessibleFolders >= 0)

        return scanned < maxDocuments &&
            pendingFolders == 0 &&
            !documentLimitReached &&
            !depthLimited &&
            inaccessibleFolders == 0
    }
}
