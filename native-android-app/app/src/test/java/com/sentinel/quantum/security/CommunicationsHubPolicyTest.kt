package com.sentinel.quantum.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunicationsHubPolicyTest {
    @Test fun privateConversationReadingIsNeverImplicitlyGranted() {
        assertTrue(CommunicationsHubPolicy.capabilities.none { it.canReadPrivateConversations })
        assertTrue(CommunicationsHubPolicy.capabilities.none { it.canImportPrivateContacts })
    }

    @Test fun externalMessagingRequiresExplicitUserGesture() {
        assertFalse(CommunicationsHubPolicy.permitsExternalLaunch(CommunicationsHubPolicy.Channel.SIGNAL, false))
        assertTrue(CommunicationsHubPolicy.permitsExternalLaunch(CommunicationsHubPolicy.Channel.SIGNAL, true))
        assertTrue(CommunicationsHubPolicy.permitsExternalLaunch(CommunicationsHubPolicy.Channel.TELEGRAM, true))
    }

    @Test fun connectedAccountChannelsAreNotTreatedAsSimpleDeepLinks() {
        assertFalse(CommunicationsHubPolicy.permitsExternalLaunch(CommunicationsHubPolicy.Channel.TEAMS, true))
        assertFalse(CommunicationsHubPolicy.permitsExternalLaunch(CommunicationsHubPolicy.Channel.SLACK, true))
    }

    @Test fun nativeChannelsAreNotExternalLaunches() {
        assertFalse(CommunicationsHubPolicy.permitsExternalLaunch(CommunicationsHubPolicy.Channel.CALLS, true))
        assertFalse(CommunicationsHubPolicy.permitsExternalLaunch(CommunicationsHubPolicy.Channel.SMS_MMS, true))
    }
}
