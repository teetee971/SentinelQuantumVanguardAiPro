package com.sentinel.quantum.security

import com.sentinel.quantum.BuildConfig

/**
 * Single distributed-client source of truth for the public Sentinel API origin.
 *
 * The legacy BuildConfig field name is intentionally contained here until the production
 * Sentinel-owned gateway is deployed and validated. Business clients must not read that
 * build field directly.
 *
 * The host allowlist is deliberately independent from [baseUrl]. A configuration change must
 * therefore update the reviewed pin explicitly instead of making any configured origin trusted
 * automatically.
 */
object SentinelApiOrigin {
    private const val LEGACY_PINNED_HOST = "sentinel-moteur-api.onrender.com"

    val baseUrl: String
        get() = BuildConfig.WANGIRI_API_BASE_URL

    val allowedHosts: Set<String>
        get() = setOf(LEGACY_PINNED_HOST)
}
