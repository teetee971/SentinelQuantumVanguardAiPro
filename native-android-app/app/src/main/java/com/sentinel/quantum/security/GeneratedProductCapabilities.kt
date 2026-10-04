// Generated from config/product-capabilities.json. Do not edit.
package com.sentinel.quantum.security

object GeneratedProductCapabilities {
    data class State(val implemented: Boolean, val customerAvailable: Boolean, val expiresAtMs: Long?) {
        fun availableAt(nowMs: Long = System.currentTimeMillis()): Boolean =
            customerAvailable && expiresAtMs != null && nowMs >= 0L && nowMs < expiresAtMs
    }
    val states: Map<String, State> = mapOf(
        "phone_core_android" to State(true, false, null),
        "sentinel_vpn_service" to State(true, false, null),
        "voice_transform_calls" to State(true, false, null),
        "geointel_usgs" to State(true, false, null),
        "geointel_multisource" to State(false, false, null),
        "collective_defense_backend" to State(true, false, null),
        "collective_defense_android" to State(true, false, null),
        "signed_phone_reputation_feed" to State(true, false, null),
        "phone_moderation_backend" to State(true, false, null),
        "repository_i18n" to State(true, false, null),
        "android_distribution_compliance" to State(true, false, null),
        "public_android_release" to State(true, false, null),
        "saas_identity" to State(false, false, null),
        "voice_call_infrastructure" to State(false, false, null),
        "vpn_sequence_authority" to State(true, false, null),
        "mesh_runtime" to State(true, false, null),
        "osint_android_feeds" to State(true, false, null)
    )
}
