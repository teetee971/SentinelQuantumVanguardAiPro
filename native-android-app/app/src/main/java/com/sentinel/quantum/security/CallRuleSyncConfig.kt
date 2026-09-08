package com.sentinel.quantum.security

import java.security.PublicKey

/**
 * Fixed, compile-time configuration for the optional signed call-reputation sync feature.
 *
 * This intentionally ships with no trusted keys and [SYNC_ENABLED] set to `false`. Wiring
 * [CallRuleSyncClient] end-to-end (see [CallBlockingScreen]) proves the client-side path
 * compiles and behaves correctly, but a real, externally verified issuer, endpoint and
 * public key must be provisioned as a separate, reviewed deployment step before this is
 * presented to users as a live feed. Never invent production keys or issuers here.
 */
object CallRuleSyncConfig {
    /** Master switch: while false, no sync UI or network call is offered to users. */
    const val SYNC_ENABLED = false

    /** Single allowlisted, HTTPS-only endpoint. Never user-editable. */
    const val ENDPOINT = "https://example.invalid/sentinel/call-rules-fr-vigilance.txt"
    val ALLOWED_HOSTS: Set<String> = setOf("example.invalid")

    const val EXPECTED_ISSUER_ID = "sentinel-fr-vigilance-issuer"

    /** No production public key has been provisioned yet; see class documentation. */
    val TRUSTED_KEYS: Map<String, PublicKey> = emptyMap()
}
