package com.sentinel.quantum.auth

/**
 * Non-secret Auth0 client configuration. Client IDs and domains are public app metadata;
 * tokens and refresh credentials remain inside the SDK's encrypted credential manager.
 */
data class Auth0Configuration(
    val domain: String,
    val clientId: String,
    val scheme: String,
    val audience: String
) {
    fun validate(): List<String> = buildList {
        if (domain.isBlank() || domain.contains("://") || !domain.contains('.')) {
            add("AUTH0_DOMAIN_INVALID")
        }
        if (clientId.isBlank()) add("AUTH0_CLIENT_ID_MISSING")
        if (!scheme.matches(Regex("[a-z][a-z0-9+.-]*"))) add("AUTH0_SCHEME_INVALID")
        if (!audience.startsWith("https://")) add("AUTH0_AUDIENCE_INVALID")
    }
}
