package com.sentinel.quantum.auth

private const val AUTH0_ERROR_FALLBACK = "Connexion impossible. Aucun jeton n’a été conservé."

/** Keeps provider diagnostics visible without exposing credentials or raw token data. */
internal fun auth0ErrorMessage(providerMessage: String?): String {
    val detail = providerMessage?.trim().orEmpty()
    return if (detail.isEmpty()) AUTH0_ERROR_FALLBACK else "Connexion impossible : $detail"
}
