package com.sentinel.quantum.security

/**
 * Per-peer GATT control-plane state. No identity or cryptographic trust is inferred
 * from a BLE address, connection, MTU, or CCCD subscription.
 *
 * Main-thread confinement is required by the Android adapter. Session tokens prevent
 * delayed callbacks from a disconnected peer from changing a newer session.
 * Application payloads remain disabled until a separately reviewed authenticated
 * transport is attached.
 */
internal class OfflineMeshGattPeerSessions(private val maximumPeers: Int = 8) {
    init { require(maximumPeers in 1..32) }

    data class Token(val peerKey: String, val generation: Long)
    data class Snapshot(
        val connected: Boolean,
        val mtu: Int,
        val notificationsEnabled: Boolean,
        val privateDataAllowed: Boolean
    )

    private data class Session(
        val token: Token,
        var mtu: Int = 23,
        var notificationsEnabled: Boolean = false,
        var privateDataAllowed: Boolean = false
    )

    private val sessions = mutableMapOf<String, Session>()
    private var sequence = 0L

    fun connect(peerKey: String): Token? {
        if (peerKey.isBlank() || sessions.containsKey(peerKey) || sessions.size >= maximumPeers) return null
        val token = Token(peerKey, ++sequence)
        sessions[peerKey] = Session(token)
        return token
    }

    fun updateMtu(token: Token, mtu: Int): Boolean {
        val session = current(token) ?: return false
        if (mtu !in 23..517) return false
        session.mtu = mtu
        return true
    }

    fun setNotifications(token: Token, enabled: Boolean): Boolean {
        val session = current(token) ?: return false
        session.notificationsEnabled = enabled
        if (!enabled) session.privateDataAllowed = false
        return true
    }

    fun setPrivateDataAllowed(token: Token, allowed: Boolean): Boolean {
        val session = current(token) ?: return false
        if (allowed && !session.notificationsEnabled) return false
        session.privateDataAllowed = allowed
        return true
    }

    fun snapshot(token: Token): Snapshot? = current(token)?.let {
        Snapshot(true, it.mtu, it.notificationsEnabled, false)
    }

    fun canSendControlNotification(token: Token, bytes: Int): Boolean {
        val session = current(token) ?: return false
        return session.notificationsEnabled && bytes in 1..minOf(244, session.mtu - 3)
    }

    /** Only the adapter's authenticated secure channel may enable this flag. */
    fun canTransferPrivateData(token: Token): Boolean = current(token)?.privateDataAllowed == true

    fun disconnect(token: Token): Boolean {
        if (current(token) == null) return false
        sessions.remove(token.peerKey)
        return true
    }

    fun close() { sessions.clear() }

    fun peerCount(): Int = sessions.size

    private fun current(token: Token): Session? =
        sessions[token.peerKey]?.takeIf { it.token == token }
}
