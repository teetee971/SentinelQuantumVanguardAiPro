package com.sentinel.quantum.smarthome

data class SmartHomeIntegration(
    val brand: SmartHomeBrand,
    val label: String,
    val supportedTransports: Set<SmartHomeTransport>,
    val discoveryMode: String,
    val controlMode: String
)

object SmartHomeIntegrationRegistry {
    val integrations = listOf(
        SmartHomeIntegration(SmartHomeBrand.MATTER, "Matter",
            setOf(SmartHomeTransport.MATTER_WIFI, SmartHomeTransport.MATTER_THREAD),
            "Commissioning Android avec consentement utilisateur", "Clusters Matter autorisés"),
        SmartHomeIntegration(SmartHomeBrand.PHILIPS_HUE, "Philips Hue",
            setOf(SmartHomeTransport.ETHERNET, SmartHomeTransport.WIFI, SmartHomeTransport.ZIGBEE_BRIDGE, SmartHomeTransport.MATTER_WIFI),
            "Bridge local / Matter", "API Hue autorisée ou Matter"),
        SmartHomeIntegration(SmartHomeBrand.LSC_SMART_CONNECT, "LSC Smart Connect",
            setOf(SmartHomeTransport.WIFI, SmartHomeTransport.MATTER_WIFI, SmartHomeTransport.MATTER_THREAD, SmartHomeTransport.CLOUD),
            "Matter lorsque disponible ; fournisseur sinon", "Matter local ou API fournisseur autorisée"),
        SmartHomeIntegration(SmartHomeBrand.IMOU, "Imou",
            setOf(SmartHomeTransport.WIFI, SmartHomeTransport.ETHERNET, SmartHomeTransport.CLOUD),
            "Découverte locale bornée + connecteur autorisé", "API officielle vérifiée uniquement"),
        SmartHomeIntegration(SmartHomeBrand.TP_LINK_TAPO_KASA, "TP-Link Tapo / Kasa",
            setOf(SmartHomeTransport.WIFI, SmartHomeTransport.MATTER_WIFI, SmartHomeTransport.CLOUD),
            "Matter ou découverte locale autorisée", "Matter / interface fournisseur vérifiée"),
        SmartHomeIntegration(SmartHomeBrand.SAMSUNG_SMARTTHINGS, "Samsung SmartThings",
            setOf(SmartHomeTransport.WIFI, SmartHomeTransport.MATTER_WIFI, SmartHomeTransport.MATTER_THREAD, SmartHomeTransport.CLOUD),
            "Matter / intégration SmartThings autorisée", "API/consentement utilisateur"),
        SmartHomeIntegration(SmartHomeBrand.GOOGLE_HOME, "Google Home",
            setOf(SmartHomeTransport.WIFI, SmartHomeTransport.MATTER_WIFI, SmartHomeTransport.MATTER_THREAD, SmartHomeTransport.CLOUD),
            "Matter et interfaces Google autorisées", "Consentement utilisateur"),
        SmartHomeIntegration(SmartHomeBrand.AMAZON_ALEXA, "Amazon Alexa / Echo",
            setOf(SmartHomeTransport.WIFI, SmartHomeTransport.MATTER_WIFI, SmartHomeTransport.MATTER_THREAD, SmartHomeTransport.CLOUD),
            "Matter / intégration autorisée", "Consentement utilisateur"),
        SmartHomeIntegration(SmartHomeBrand.APPLE_HOME, "Apple Home",
            setOf(SmartHomeTransport.WIFI, SmartHomeTransport.MATTER_WIFI, SmartHomeTransport.MATTER_THREAD),
            "Matter observable depuis Android lorsque permis", "Pas de contournement de l'écosystème Apple"),
        SmartHomeIntegration(SmartHomeBrand.HOME_ASSISTANT, "Home Assistant",
            setOf(SmartHomeTransport.ETHERNET, SmartHomeTransport.WIFI, SmartHomeTransport.MATTER_WIFI, SmartHomeTransport.MATTER_THREAD, SmartHomeTransport.ZIGBEE_BRIDGE, SmartHomeTransport.Z_WAVE_BRIDGE),
            "Instance locale explicitement configurée", "API locale avec jeton utilisateur"),
        SmartHomeIntegration(SmartHomeBrand.IKEA_HOME_SMART, "IKEA Home smart",
            setOf(SmartHomeTransport.ZIGBEE_BRIDGE, SmartHomeTransport.MATTER_THREAD, SmartHomeTransport.MATTER_WIFI),
            "Bridge / Matter lorsque compatible", "Interface autorisée"),
        SmartHomeIntegration(SmartHomeBrand.XIAOMI_AQARA, "Xiaomi / Aqara",
            setOf(SmartHomeTransport.WIFI, SmartHomeTransport.ZIGBEE_BRIDGE, SmartHomeTransport.MATTER_THREAD, SmartHomeTransport.CLOUD),
            "Matter / hub / découverte autorisée", "Interface autorisée")
    )
}
