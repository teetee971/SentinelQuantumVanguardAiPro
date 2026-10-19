package com.sentinel.quantum.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MailConnectorContractTest {
    private val contract = MailConnectorContract

    @Test fun noConfiguredProviderCanReadWithoutVerifiedGrant() {
        for (provider in MailConnectorContract.Provider.values()) {
            val connection = MailConnectorContract.Connection(provider)
            assertFalse(connection.canRead)
            assertFalse(connection.canPerform(MailConnectorContract.Capability.DELETE_MESSAGES))
        }
    }

    @Test fun connectedAccountNeedsExplicitReadScope() {
        val connection = MailConnectorContract.Connection(
            MailConnectorContract.Provider.GMAIL,
            state = MailConnectorContract.State.CONNECTED
        )
        assertFalse(connection.canRead)
    }

    @Test fun readOnlyConnectionCannotDeleteSendOrArchive() {
        val connection = MailConnectorContract.Connection(
            MailConnectorContract.Provider.GMAIL,
            state = MailConnectorContract.State.CONNECTED,
            grantedCapabilities = setOf(MailConnectorContract.Capability.READ_MESSAGES)
        )
        assertTrue(connection.canRead)
        assertFalse(connection.canPerform(MailConnectorContract.Capability.SEND_MESSAGES))
        assertFalse(connection.canPerform(MailConnectorContract.Capability.DELETE_MESSAGES))
        assertFalse(connection.canPerform(MailConnectorContract.Capability.ARCHIVE_MESSAGES))
    }

    @Test fun revokedGrantBlocksAllActionsEvenWhenScopeSetIsStale() {
        val connection = MailConnectorContract.Connection(
            MailConnectorContract.Provider.MICROSOFT_365,
            state = MailConnectorContract.State.REVOKED,
            grantedCapabilities = MailConnectorContract.Capability.values().toSet()
        )
        assertFalse(connection.canRead)
        for (capability in MailConnectorContract.Capability.values()) {
            assertFalse(connection.canPerform(capability))
        }
    }
}
