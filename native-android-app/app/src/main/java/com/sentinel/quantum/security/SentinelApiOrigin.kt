package com.sentinel.quantum.security

import com.sentinel.quantum.BuildConfig

/**
 * Single distributed-client source of truth for the public Sentinel API origin.
 *
 * The legacy BuildConfig field name is intentionally contained here until the production
 * Sentinel-owned gateway is deployed and validated. Business clients must not read that
 * build field directly.
 */
object SentinelApiOrigin {
    val baseUrl: String
        get() = BuildConfig.WANGIRI_API_BASE_URL

    val allowedHosts: Set<String>
        get() = setOf(SentinelApiEndpointPolicy.originHost(baseUrl))
}
