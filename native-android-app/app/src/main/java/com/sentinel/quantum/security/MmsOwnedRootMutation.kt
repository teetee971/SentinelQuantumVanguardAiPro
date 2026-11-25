package com.sentinel.quantum.security

/**
 * Decides whether an MMS provider root may be deleted or moved out of OUTBOX.
 *
 * A mutation is refused unless the caller still holds the SMS role and the row that
 * would be touched is the same message this app inserted. The returned clauses are the
 * selection that must accompany the provider call, so a concurrent change cannot make
 * the write land on another row.
 */
internal object MmsOwnedRootMutation {
    const val BOX_INBOX = 1
    const val BOX_SENT = 2
    const val BOX_OUTBOX = 4
    const val BOX_FAILED = 5

    enum class Column { ID, BOX, CREATOR, TRANSACTION, MESSAGE_ID, DATE, SUBSCRIPTION }

    data class Clause(val column: Column, val value: String?, val isNull: Boolean = false)

    data class Observed(
        val id: Long,
        val creator: String?,
        val messageBox: Int,
        val transactionId: String?,
        val messageId: String? = null,
        val dateSeconds: Long? = null,
        val subscriptionId: Int? = null
    )

    data class OutgoingIdentity(
        val providerMessageId: Long,
        val packageName: String,
        val transactionId: String
    )

    data class IncomingIdentity(
        val providerMessageId: Long,
        val packageName: String,
        val messageId: String?,
        val transactionId: String?,
        val dateSeconds: Long,
        val subscriptionId: Int
    )

    sealed interface Decision {
        data class Mutate(val clauses: List<Clause>) : Decision
        data object AlreadyDone : Decision
        data object Absent : Decision
        data object Refuse : Decision
    }

    enum class OutgoingPhase {
        BUILDING,
        ROOT_INSERTED,
        READY,
        SUBMITTED,
        SUBMISSION_UNKNOWN,
        RESULT_SENT,
        RESULT_FAILED,
        REJECTED_BEFORE_TRANSPORT
    }

    enum class RepairAction {
        DELETE_OWNED_INCOMPLETE,
        FINALIZE,
        KEEP_ROW,
        RETRY_LATER
    }

    fun repairAction(phase: OutgoingPhase, roleHeld: Boolean): RepairAction {
        if (!roleHeld) return RepairAction.RETRY_LATER
        return when (phase) {
            OutgoingPhase.READY,
            OutgoingPhase.SUBMITTED,
            OutgoingPhase.SUBMISSION_UNKNOWN -> RepairAction.KEEP_ROW
            OutgoingPhase.RESULT_SENT,
            OutgoingPhase.RESULT_FAILED,
            OutgoingPhase.REJECTED_BEFORE_TRANSPORT -> RepairAction.FINALIZE
            OutgoingPhase.BUILDING,
            OutgoingPhase.ROOT_INSERTED -> RepairAction.DELETE_OWNED_INCOMPLETE
        }
    }

    fun authorizeOutgoingDelete(
        roleHeld: Boolean,
        expected: OutgoingIdentity,
        observed: Observed?
    ): Decision {
        if (!roleHeld || !validOutgoing(expected)) return Decision.Refuse
        if (observed == null) return Decision.Absent
        if (!sameOutgoing(expected, observed) || observed.messageBox != BOX_OUTBOX) {
            return Decision.Refuse
        }
        return Decision.Mutate(outgoingClauses(expected))
    }

    fun authorizeOutgoingFinalize(
        roleHeld: Boolean,
        expected: OutgoingIdentity,
        successful: Boolean,
        observed: Observed?
    ): Decision {
        if (!roleHeld || !validOutgoing(expected)) return Decision.Refuse
        if (observed == null || !sameOutgoing(expected, observed)) return Decision.Refuse
        val target = targetBox(successful)
        if (observed.messageBox == target) return Decision.AlreadyDone
        if (observed.messageBox != BOX_OUTBOX) return Decision.Refuse
        return Decision.Mutate(outgoingClauses(expected))
    }

    fun authorizeIncomingDelete(
        roleHeld: Boolean,
        expected: IncomingIdentity,
        observed: Observed?
    ): Decision {
        if (!roleHeld || expected.providerMessageId <= 0L || expected.packageName.isBlank()) {
            return Decision.Refuse
        }
        if (observed == null) return Decision.Absent
        if (
            observed.id != expected.providerMessageId ||
            observed.creator != expected.packageName ||
            observed.messageBox != BOX_INBOX ||
            observed.dateSeconds != expected.dateSeconds ||
            observed.subscriptionId != expected.subscriptionId ||
            observed.messageId != expected.messageId ||
            observed.transactionId != expected.transactionId
        ) {
            return Decision.Refuse
        }
        return Decision.Mutate(incomingClauses(expected))
    }

    fun targetBox(successful: Boolean): Int = if (successful) BOX_SENT else BOX_FAILED

    /**
     * Applies a delete only when [decision] carries an ownership selection.
     * Returns true when the row is already absent or the selection removed it and a
     * re-read no longer sees it. A concurrent identity change leaves the row in place.
     */
    fun applyDelete(
        decision: Decision,
        deleteMatching: (List<Clause>) -> Int,
        reread: () -> Observed?
    ): Boolean {
        return when (decision) {
            Decision.Absent -> true
            Decision.AlreadyDone, Decision.Refuse -> false
            is Decision.Mutate -> {
                if (!bindsOwnership(decision.clauses)) return false
                val deleted = deleteMatching(decision.clauses)
                deleted >= 0 && reread() == null
            }
        }
    }

    /**
     * Moves OUTBOX to SENT or FAILED only under the ownership selection.
     * A row that already has the target box is unchanged. A row that changed to the
     * other terminal box between the read and the write is left untouched.
     */
    fun applyFinalize(
        decision: Decision,
        targetBox: Int,
        updateMatching: (List<Clause>) -> Int,
        reread: () -> Observed?
    ): Boolean {
        return when (decision) {
            Decision.AlreadyDone -> true
            Decision.Absent, Decision.Refuse -> false
            is Decision.Mutate -> {
                if (!bindsOwnership(decision.clauses)) return false
                val updated = updateMatching(decision.clauses)
                if (updated < 0) return false
                val after = reread() ?: return false
                after.messageBox == targetBox &&
                    after.creator == clauseValue(decision.clauses, Column.CREATOR) &&
                    after.transactionId == clauseValue(decision.clauses, Column.TRANSACTION) &&
                    after.id.toString() == clauseValue(decision.clauses, Column.ID)
            }
        }
    }

    fun matches(observed: Observed?, clauses: List<Clause>): Boolean {
        if (observed == null) return false
        return clauses.all { clause ->
            val actual = when (clause.column) {
                Column.ID -> observed.id.toString()
                Column.BOX -> observed.messageBox.toString()
                Column.CREATOR -> observed.creator
                Column.TRANSACTION -> observed.transactionId
                Column.MESSAGE_ID -> observed.messageId
                Column.DATE -> observed.dateSeconds?.toString()
                Column.SUBSCRIPTION -> observed.subscriptionId?.toString()
            }
            if (clause.isNull) actual == null else actual == clause.value
        }
    }

    fun bindsOwnership(clauses: List<Clause>): Boolean {
        val columns = clauses.map { it.column }.toSet()
        if (Column.ID !in columns || Column.CREATOR !in columns || Column.BOX !in columns) {
            return false
        }
        return clauses.none { it.column == Column.CREATOR && (it.isNull || it.value.isNullOrBlank()) }
    }

    private fun validOutgoing(expected: OutgoingIdentity): Boolean =
        expected.providerMessageId > 0L &&
            expected.packageName.isNotBlank() &&
            expected.transactionId.isNotBlank()

    private fun sameOutgoing(expected: OutgoingIdentity, observed: Observed): Boolean =
        observed.id == expected.providerMessageId &&
            observed.creator == expected.packageName &&
            observed.transactionId == expected.transactionId

    private fun outgoingClauses(expected: OutgoingIdentity): List<Clause> = listOf(
        Clause(Column.ID, expected.providerMessageId.toString()),
        Clause(Column.CREATOR, expected.packageName),
        Clause(Column.BOX, BOX_OUTBOX.toString()),
        Clause(Column.TRANSACTION, expected.transactionId)
    )

    private fun incomingClauses(expected: IncomingIdentity): List<Clause> {
        val clauses = mutableListOf(
            Clause(Column.ID, expected.providerMessageId.toString()),
            Clause(Column.CREATOR, expected.packageName),
            Clause(Column.BOX, BOX_INBOX.toString()),
            Clause(Column.DATE, expected.dateSeconds.toString()),
            Clause(Column.SUBSCRIPTION, expected.subscriptionId.toString())
        )
        clauses += nullable(Column.MESSAGE_ID, expected.messageId)
        clauses += nullable(Column.TRANSACTION, expected.transactionId)
        return clauses
    }

    private fun nullable(column: Column, value: String?): Clause =
        if (value == null) Clause(column, null, isNull = true) else Clause(column, value)

    private fun clauseValue(clauses: List<Clause>, column: Column): String? =
        clauses.firstOrNull { it.column == column }?.value
}
