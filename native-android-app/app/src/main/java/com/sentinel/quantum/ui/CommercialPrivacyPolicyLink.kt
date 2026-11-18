package com.sentinel.quantum.ui

/**
 * URL opened from Phone Core V1 settings.
 *
 * [VERIFIED_V1_URL] stays null until an HTTPS GET of that exact address returns the Phone Core V1
 * policy itself. On 18 November 2026 the live host answered candidate paths with the site shell,
 * while [SHOWCASE_URL] returned the September 2026 showcase policy. A null V1 URL must not be
 * replaced with a guessed address.
 */
internal object CommercialPrivacyPolicyLink {
    const val SHOWCASE_URL = "https://sentinelquantumvanguardaipro.pages.dev/public/privacy.html"
    val VERIFIED_V1_URL: String? = null

    fun url(): String = VERIFIED_V1_URL ?: SHOWCASE_URL

    fun opensPhoneCorePolicy(): Boolean = !VERIFIED_V1_URL.isNullOrBlank()
}
