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
        SmartHomeIntegration(
            SmartHomeBrand.PHILIPS_HUE,
            "Philips Hue",
            setOf(SmartHomeTransport.ETHERNET, SmartHomeTransport.WIFI, SmartHomeTransport.ZIGBEE_BRIDGE, SmartHomeTransport.MATTER_WIFI),
            "Bridge local / Matter",
            "API Hue autorisée ou Matter"
        ),
        SmartHomeIntegration(
            SmartHomeBrand.LSC_SMART_CONNECT,
            "LSC Smart Connect",
            setOf(SmartHomeTransport.WIFI, SmartHomeTransport.MATTER_WIFI, SmartHomeTransport.MATTER_THREAD, SmartHomeTransport.CLOUD),
            "Matter lorsque disponible ; connecteur fournisseur à autoriser sinon",
            "Matter local ou API fournisseur autorisée"
        ),
        SmartHomeIntegration(
            SmartHomeBrand.IMOU,
            "Imou",
            setOf(SmartHomeTransport.WIFI, SmartHomeTransport.ETHERNET, SmartHomeTransport.CLOUD),
            "Découverte locale bornée + connecteur fournisseur autorisé",
            "Aucune commande propriétaire sans API/autorisation vérifiée"
        ),
        SmartHomeIntegration(
            SmartHomeBrand.MATTER,
            "Matter",
            setOf(SmartHomeTransport.MATTER_WIFI, SmartHomeTransport.MATTER_THREAD),
            "Commissioning Android avec consentement utilisateur",
            "Contrôle local selon les clusters autorisés"
        )
    )
}
