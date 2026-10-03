package com.sentinel.quantum.security

/**
 * Pure compensated transaction for constructing one outgoing MMS provider projection.
 *
 * The Android MMS provider spans several rows (PDU/address/parts) and does not expose an app-level
 * SQL transaction. We therefore journal before the first provider mutation and delete the root PDU
 * on every incomplete build; TelephonyProvider cascades root deletion to address/part rows.
 */
internal object MmsProviderProjectionTransaction {
    interface Operations {
        fun beginJournal(): Boolean
        fun insertRoot(): Long?
        fun recordRoot(providerMessageId: Long): Boolean
        fun insertAddress(providerMessageId: Long): Boolean
        fun insertParts(providerMessageId: Long): Boolean
        fun markReady(providerMessageId: Long): Boolean
        fun deleteRoot(providerMessageId: Long): Boolean
        fun clearJournal(): Boolean
    }

    sealed interface Result {
        data class Ready(val providerMessageId: Long) : Result
        data class Rejected(
            val reason: String,
            val cleanupConfirmed: Boolean
        ) : Result
    }

    fun execute(operations: Operations): Result {
        if (!operations.beginJournal()) {
            return Result.Rejected("MMS_PROVIDER_JOURNAL_UNAVAILABLE", cleanupConfirmed = true)
        }

        val providerMessageId = operations.insertRoot()
            ?: return Result.Rejected(
                reason = "MMS_PROVIDER_ROOT_INSERT_FAILED",
                cleanupConfirmed = operations.clearJournal()
            )

        fun rollback(reason: String): Result.Rejected {
            val rootDeleted = operations.deleteRoot(providerMessageId)
            val journalCleared = rootDeleted && operations.clearJournal()
            return Result.Rejected(reason, cleanupConfirmed = rootDeleted && journalCleared)
        }

        if (!operations.recordRoot(providerMessageId)) {
            return rollback("MMS_PROVIDER_ROOT_JOURNAL_FAILED")
        }
        if (!operations.insertAddress(providerMessageId)) {
            return rollback("MMS_PROVIDER_ADDRESS_INSERT_FAILED")
        }
        if (!operations.insertParts(providerMessageId)) {
            return rollback("MMS_PROVIDER_PART_INSERT_FAILED")
        }
        if (!operations.markReady(providerMessageId)) {
            return rollback("MMS_PROVIDER_READY_JOURNAL_FAILED")
        }

        return Result.Ready(providerMessageId)
    }
}
