package com.sentinel.quantum.security

/**
 * Pure selection policy for the future Android VPN country picker.
 *
 * It never invents gateways: choices are derived only from an already-verified signed catalog,
 * and only countries with at least one currently selectable AVAILABLE gateway are exposed.
 */
object VpnGatewaySelectionPolicy {
    data class CountryChoice(
        val countryCode: String,
        val bestGatewayId: String
    )

    fun availableCountries(
        catalog: SignedVpnGatewayCatalogVerifier.Catalog,
        now: Long
    ): List<CountryChoice> {
        if (now < 0L || now >= catalog.expiresAtMs) return emptyList()

        return catalog.gateways
            .map { it.countryCode }
            .distinct()
            .sorted()
            .mapNotNull { country ->
                val best = catalog.selectBestAvailable(country, now) ?: return@mapNotNull null
                CountryChoice(country, best.id)
            }
    }

    fun select(
        catalog: SignedVpnGatewayCatalogVerifier.Catalog,
        countryCode: String,
        now: Long
    ): SignedVpnGatewayCatalogVerifier.Gateway? {
        if (!countryCode.matches(Regex("[A-Z]{2}"))) return null
        return catalog.selectBestAvailable(countryCode, now)
    }
}
