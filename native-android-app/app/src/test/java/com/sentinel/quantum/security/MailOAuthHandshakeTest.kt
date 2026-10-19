package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MailOAuthHandshakeTest {
    private val callback = "https://auth.sentinel.example/mail/callback"
    private val allowlist = mapOf(MailConnectorContract.Provider.GMAIL to setOf(callback))

    @Test fun freshHandshakeHasRandomStateAndValidPkceLengths() {
        val h = MailOAuthHandshake(nowEpochMs = { 1_000L }, approvedRedirects = allowlist)
        val one = h.begin(MailConnectorContract.Provider.GMAIL, callback)
        val two = h.begin(MailConnectorContract.Provider.GMAIL, callback)
        assertNotEquals(one.state, two.state)
        assertNotEquals(one.codeVerifier, two.codeVerifier)
        assertEquals(43, two.codeChallenge.length)
        assertFalse(two.codeChallenge.contains("="))
    }

    @Test fun validCallbackMayBeConsumedExactlyOnce() {
        val h = MailOAuthHandshake(nowEpochMs = { 1_000L }, approvedRedirects = allowlist)
        val p = h.begin(MailConnectorContract.Provider.GMAIL, callback)
        assertEquals(MailOAuthHandshake.Check.ACCEPTED,
            h.consume(p.provider, callback, p.state))
        assertEquals(MailOAuthHandshake.Check.MISSING,
            h.consume(p.provider, callback, p.state))
    }

    @Test fun alteredStateInvalidatesPendingTransaction() {
        val h = MailOAuthHandshake(nowEpochMs = { 1_000L }, approvedRedirects = allowlist)
        val p = h.begin(MailConnectorContract.Provider.GMAIL, callback)
        assertEquals(MailOAuthHandshake.Check.STATE_MISMATCH,
            h.consume(p.provider, callback, p.state + "x"))
        assertEquals(MailOAuthHandshake.Check.MISSING,
            h.consume(p.provider, callback, p.state))
    }

    @Test fun replayExpiredOrWrongProviderIsRejected() {
        var time = 1_000L
        val h = MailOAuthHandshake(nowEpochMs = { time }, approvedRedirects = allowlist)
        val p = h.begin(MailConnectorContract.Provider.GMAIL, callback)
        assertEquals(MailOAuthHandshake.Check.PROVIDER_MISMATCH,
            h.consume(MailConnectorContract.Provider.MICROSOFT_365, callback, p.state))
        val p2 = h.begin(MailConnectorContract.Provider.GMAIL, callback)
        time += 300_001L
        assertEquals(MailOAuthHandshake.Check.EXPIRED,
            h.consume(p2.provider, callback, p2.state))
    }

    @Test fun callbackUriMustMatchExactly() {
        val h = MailOAuthHandshake(nowEpochMs = { 1_000L }, approvedRedirects = allowlist)
        val p = h.begin(MailConnectorContract.Provider.GMAIL, callback)
        assertEquals(MailOAuthHandshake.Check.REDIRECT_MISMATCH,
            h.consume(p.provider, callback + "/other", p.state))
    }

    @Test(expected = IllegalArgumentException::class)
    fun insecureRedirectIsRejected() {
        MailOAuthHandshake().begin(MailConnectorContract.Provider.GMAIL,
            "http://auth.sentinel.example/callback")
    }

    @Test(expected = IllegalArgumentException::class)
    fun imapHasNoGenericOAuthShortcut() {
        MailOAuthHandshake().begin(MailConnectorContract.Provider.IMAP, callback)
    }
}
