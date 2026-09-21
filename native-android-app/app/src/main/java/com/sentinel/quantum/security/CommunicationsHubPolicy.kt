package com.sentinel.quantum.security

/**
 * Capability registry for Sentinel's communications hub.
 *
 * This deliberately models only user-initiated interoperability. It does not grant Sentinel
 * permission to read another app's private messages, contacts, notification history or database.
 */
object CommunicationsHubPolicy {
    enum class Channel {
        CALLS,
        SMS_MMS,
        WHATSAPP,
        SIGNAL,
        TELEGRAM,
        MESSENGER,
        INSTAGRAM,
        DISCORD,
        TEAMS,
        SLACK
    }

    enum class Integration {
        NATIVE,
        USER_INITIATED_EXTERNAL,
        CONNECTED_ACCOUNT_REQUIRED
    }

    data class Capability(
        val channel: Channel,
        val integration: Integration,
        val canReadPrivateConversations: Boolean = false,
        val canImportPrivateContacts: Boolean = false
    )

    val capabilities: List<Capability> = listOf(
        Capability(Channel.CALLS, Integration.NATIVE),
        Capability(Channel.SMS_MMS, Integration.NATIVE),
        Capability(Channel.WHATSAPP, Integration.USER_INITIATED_EXTERNAL),
        Capability(Channel.SIGNAL, Integration.USER_INITIATED_EXTERNAL),
        Capability(Channel.TELEGRAM, Integration.USER_INITIATED_EXTERNAL),
        Capability(Channel.MESSENGER, Integration.USER_INITIATED_EXTERNAL),
        Capability(Channel.INSTAGRAM, Integration.USER_INITIATED_EXTERNAL),
        Capability(Channel.DISCORD, Integration.USER_INITIATED_EXTERNAL),
        Capability(Channel.TEAMS, Integration.CONNECTED_ACCOUNT_REQUIRED),
        Capability(Channel.SLACK, Integration.CONNECTED_ACCOUNT_REQUIRED)
    )

    fun capability(channel: Channel): Capability = capabilities.first { it.channel == channel }

    /**
     * Cross-app actions must remain explicit user gestures. No background harvesting or
     * inference of social identities from a private phone number is permitted.
     */
    fun permitsExternalLaunch(channel: Channel, userInitiated: Boolean): Boolean =
        userInitiated && capability(channel).integration == Integration.USER_INITIATED_EXTERNAL
}
