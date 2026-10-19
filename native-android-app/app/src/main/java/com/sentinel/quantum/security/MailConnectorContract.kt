package com.sentinel.quantum.security

/**
 * Provider-neutral mailbox integration contract.
 *
 * This is NOT an active provider connection. A backend OAuth broker and verified
 * provider adapters are required before connection state can become CONNECTED.
 * Never put client secrets, provider refresh tokens or IMAP passwords in the APK.
 */
object MailConnectorContract {
    enum class Provider { GMAIL, MICROSOFT_365, IMAP }
    enum class State {
        NOT_CONFIGURED, AUTHORIZATION_REQUIRED, CONNECTING, CONNECTED,
        EXPIRED, REVOKED, ERROR
    }
    enum class Capability {
        READ_MESSAGES, READ_ATTACHMENTS, LABEL_MESSAGES, ARCHIVE_MESSAGES,
        SEND_MESSAGES, DELETE_MESSAGES
    }

    data class Connection(
        val provider: Provider,
        val state: State = State.NOT_CONFIGURED,
        val grantedCapabilities: Set<Capability> = emptySet(),
        val lastVerifiedEpochMs: Long? = null,
        val errorCode: String? = null
    ) {
        val canRead: Boolean
            get() = state == State.CONNECTED &&
                Capability.READ_MESSAGES in grantedCapabilities
        fun canPerform(capability: Capability): Boolean =
            state == State.CONNECTED && capability in grantedCapabilities
    }

    data class MessageReference(
        val provider: Provider,
        val opaqueMessageId: String,
        val receivedEpochMs: Long?,
        val sourceAccountId: String
    )

    data class ScanResult(
        val reference: MessageReference,
        val status: ScanStatus,
        val findingCodes: List<String>,
        val evaluatedEpochMs: Long
    )
    enum class ScanStatus { NOT_SCANNED, ANALYZED, LIMITED, ERROR }

    /**
     * OAuth and permissions are handled by a trusted provider adapter.
     * No authorization URL or token is synthesized by this contract.
     */
    interface Adapter {
        val provider: Provider
        suspend fun connection(): Connection
        suspend fun recentMessageRefs(maximum: Int): List<MessageReference>
        suspend fun inspectMessage(reference: MessageReference): ScanResult
    }
}
