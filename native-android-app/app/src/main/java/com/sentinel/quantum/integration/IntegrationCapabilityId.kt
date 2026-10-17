package com.sentinel.quantum.integration

enum class IntegrationCapabilityId(val wireName: String) {
    DIALER("dialer"),
    INCALL("incall"),
    CALL_SCREENING("call_screening"),
    CALLER_ID_LOCAL("caller_id_local"),
    CALLER_REPUTATION_REMOTE("caller_reputation_remote"),
    COMMUNITY_PHONE_REPORTS("community_phone_reports"),
    SMS("sms"),
    MMS("mms"),
    CONTACTS("contacts"),
    WIFI_OBSERVATION("wifi_observation"),
    BLUETOOTH_OBSERVATION("bluetooth_observation"),
    NETWORK_BEHAVIOR("network_behavior"),
    VPN_RUNTIME("vpn_runtime"),
    VPN_CONTROL_PLANE("vpn_control_plane"),
    MESH_RUNTIME("mesh_runtime"),
    WEARABLE_RUNTIME("wearable_runtime"),
    DIGITAL_EXPOSURE("digital_exposure"),
    THREAT_INTELLIGENCE("threat_intelligence"),
    REGULATORY_INTELLIGENCE("regulatory_intelligence"),
    SOC_DECISION_PLANE("soc_decision_plane"),
    OSINT_ANALYST_TOOLING("osint_analyst_tooling"),
    DEVICE_UTILITIES("device_utilities"),
    VOICE_RTC_PTT("voice_rtc_ptt"),
    FIRST_RUN_ACTIVATION("first_run_activation"),
    SYSTEM_DOCTOR_RELEASE_EVIDENCE("system_doctor_release_evidence");

    companion object {
        fun fromWireName(value: String): IntegrationCapabilityId? =
            entries.firstOrNull { it.wireName == value }
    }
}
