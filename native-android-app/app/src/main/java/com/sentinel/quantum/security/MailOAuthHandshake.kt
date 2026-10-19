package com.sentinel.quantum.security

import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * One-time OAuth authorization-code/PKCE transaction for a mailbox provider.
 *
 * This is a local protocol guard, NOT a Google/Microsoft OAuth connection.
 * The backend must independently validate state, redirect URI, identity,
 * PKCE and delegated scopes; exchange codes, store encrypted refresh tokens,
 * and bind the mailbox account to the authenticated Sentinel user.
 */
class MailOAuthHandshake(
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
    private val random: SecureRandom = SecureRandom(),
    private val approvedRedirects: Map<MailConnectorContract.Provider, Set<String>> = emptyMap()
) {
    data class Pending internal constructor(
        val provider: MailConnectorContract.Provider,
        val redirectUri: String,
        val state: String,
        val codeVerifier: String,
        val codeChallenge: String,
        val createdEpochMs: Long
    )

    enum class Check { ACCEPTED, MISSING, EXPIRED, STATE_MISMATCH, PROVIDER_MISMATCH, REDIRECT_MISMATCH }

    private var pending: Pending? = null

    @Synchronized
    fun begin(provider: MailConnectorContract.Provider, redirectUri: String): Pending {
        require(provider != MailConnectorContract.Provider.IMAP) {
            "IMAP requires an explicit provider-specific OAuth implementation"
        }
        require(redirectUri.length in 1..2048) { "Invalid callback URI" }
        val uri = runCatching { URI(redirectUri) }.getOrNull()
        require(uri != null && uri.scheme.equals("https", ignoreCase = false) &&
            uri.host != null && uri.rawUserInfo == null && uri.rawFragment == null &&
            uri.port == -1 && uri.rawQuery == null && uri.toASCIIString() == redirectUri) {
            "Only canonical HTTPS callback URIs are allowed"
        }
        require(redirectUri in approvedRedirects[provider].orEmpty()) {
            "Callback must be explicitly registered for this provider"
        }
        val state = randomBase64Url(32)
        val verifier = randomBase64Url(32)
        val challenge = base64Url(MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.US_ASCII)))
        return Pending(provider, redirectUri, state, verifier, challenge, nowEpochMs()).also {
            pending = it
        }
    }

    /**
     * Always consumes the pending transaction, including invalid callbacks.
     * Nothing here exchanges authorization codes or grants mailbox access.
     */
    @Synchronized
    fun consume(
        provider: MailConnectorContract.Provider,
        redirectUri: String,
        returnedState: String
    ): Check {
        val transaction = pending ?: return Check.MISSING
        pending = null
        val ageMs = nowEpochMs() - transaction.createdEpochMs
        if (ageMs < 0 || ageMs > 5 * 60_000L) return Check.EXPIRED
        if (provider != transaction.provider) return Check.PROVIDER_MISMATCH
        if (redirectUri != transaction.redirectUri) return Check.REDIRECT_MISMATCH
        val expected = transaction.state.toByteArray(Charsets.US_ASCII)
        val actual = returnedState.toByteArray(Charsets.US_ASCII)
        return if (MessageDigest.isEqual(expected, actual)) Check.ACCEPTED
        else Check.STATE_MISMATCH
    }

    @Synchronized fun cancel() { pending = null }

    private fun randomBase64Url(length: Int): String =
        ByteArray(length).also(random::nextBytes).let(::base64Url)

    private fun base64Url(input: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(input)
}
