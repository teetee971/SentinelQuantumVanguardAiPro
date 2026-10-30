package com.sentinel.quantum.security

/** Prevent an older asynchronous resume read from undoing newer rule edits in the displayed state. */
data class CallBlockingUiSnapshot(val snapshot: CallBlocklistStore.Snapshot, val revision: Long = 0) {
    fun afterMutation(updated: CallBlocklistStore.Snapshot): CallBlockingUiSnapshot = copy(snapshot = updated, revision = revision + 1)

    fun afterRefresh(readRevision: Long, loaded: CallBlocklistStore.Snapshot): CallBlockingUiSnapshot =
        if (readRevision == revision) copy(snapshot = loaded) else this
}
