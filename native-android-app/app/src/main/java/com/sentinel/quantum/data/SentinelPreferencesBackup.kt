package com.sentinel.quantum.data

import com.sentinel.quantum.security.CallRuleEngine
import com.sentinel.quantum.security.ProtectionMode
import com.sentinel.quantum.security.FamilySafetyPolicy
import org.json.JSONArray
import org.json.JSONObject

/**
 * Explicit user-controlled backup for restore-safe preferences only.
 *
 * Deliberately excluded:
 * - contacts, SMS/MMS content and call history;
 * - exact blocked-number fingerprints, because their HMAC key is device-bound;
 * - logs, credentials, account tokens and remote secrets.
 */
object SentinelPreferencesBackup {
    const val SCHEMA_VERSION = 1

    data class Snapshot(
        val themeMode: ThemeMode,
        val protectionMode: ProtectionMode,
        val familySafetyProfile: FamilySafetyPolicy.Profile = FamilySafetyPolicy.Profile.STANDARD,
        val callerReputationEnrichmentEnabled: Boolean,
        val osintRefreshIntervalHours: Int,
        val osintNotificationsEnabled: Boolean,
        val smsNotificationPreviewEnabled: Boolean,
        val blockedPrefixes: List<String>
    )

    fun encode(snapshot: Snapshot): String {
        val prefixes = snapshot.blockedPrefixes
            .mapNotNull(CallRuleEngine::normalizePrefix)
            .distinct()
            .take(CallRuleEngine.MAX_PREFIX_RULES)
        return JSONObject()
            .put("schema_version", SCHEMA_VERSION)
            .put("theme_mode", snapshot.themeMode.name)
            .put("protection_mode", snapshot.protectionMode.name)
            .put("family_safety_profile", snapshot.familySafetyProfile.name)
            .put("caller_reputation_enrichment_enabled", snapshot.callerReputationEnrichmentEnabled)
            .put("osint_refresh_interval_hours", SettingsStore.sanitizeInterval(snapshot.osintRefreshIntervalHours))
            .put("osint_notifications_enabled", snapshot.osintNotificationsEnabled)
            .put("sms_notification_preview_enabled", snapshot.smsNotificationPreviewEnabled)
            .put("blocked_prefixes", JSONArray(prefixes))
            .toString(2)
    }

    fun decode(raw: String): Snapshot? = runCatching {
        if (raw.length > MAX_BACKUP_CHARS) return null
        val json = JSONObject(raw)
        if (json.optInt("schema_version", -1) != SCHEMA_VERSION) return null

        val theme = ThemeMode.valueOf(json.getString("theme_mode"))
        val protection = ProtectionMode.valueOf(json.getString("protection_mode"))
        val familyProfile = FamilySafetyPolicy.Profile.valueOf(
            json.optString("family_safety_profile", FamilySafetyPolicy.Profile.STANDARD.name)
        )
        val interval = json.getInt("osint_refresh_interval_hours")
        if (SettingsStore.sanitizeInterval(interval) != interval) return null

        val prefixesJson = json.optJSONArray("blocked_prefixes") ?: JSONArray()
        if (prefixesJson.length() > CallRuleEngine.MAX_PREFIX_RULES) return null
        val prefixes = buildList {
            for (index in 0 until prefixesJson.length()) {
                val rawPrefix = prefixesJson.optString(index, "")
                val normalized = CallRuleEngine.normalizePrefix(rawPrefix) ?: return null
                add(normalized)
            }
        }.distinct()
        if (prefixes.size > CallRuleEngine.MAX_PREFIX_RULES) return null

        Snapshot(
            themeMode = theme,
            protectionMode = protection,
            familySafetyProfile = familyProfile,
            callerReputationEnrichmentEnabled = json.optBoolean("caller_reputation_enrichment_enabled", false),
            osintRefreshIntervalHours = interval,
            osintNotificationsEnabled = json.optBoolean("osint_notifications_enabled", true),
            smsNotificationPreviewEnabled = json.optBoolean("sms_notification_preview_enabled", false),
            blockedPrefixes = prefixes
        )
    }.getOrNull()

    private const val MAX_BACKUP_CHARS = 128_000
}
