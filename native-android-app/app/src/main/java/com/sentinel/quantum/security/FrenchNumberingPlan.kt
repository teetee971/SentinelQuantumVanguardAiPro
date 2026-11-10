package com.sentinel.quantum.security

/**
 * Labels from the French numbering plan. They describe the published root only.
 * They are not a block, a silence rule, or proof of who is calling.
 *
 * Public-interest roots are decision 2025-2215. Verified automated-calling roots stay in
 * [ArcepVerifiedPrefixCatalog] and are blocked only when that opt-in is on.
 */
internal object FrenchNumberingPlan {
    fun label(canonical: String): String? {
        val national = nationalForm(canonical) ?: return null
        if (ArcepVerifiedPrefixCatalog.entries.any { national.startsWith(it.nationalRoot) }) {
            return "Polyvalent vérifié"
        }
        if (PUBLIC_INTEREST.any { national.startsWith(it) }) return "Intérêt général"
        if (RESERVED.any { national.startsWith(it) }) return "Réserve, non attribué"
        return null
    }

    private fun nationalForm(canonical: String): String? = when {
        canonical.startsWith("+33") && canonical.length == 12 -> "0" + canonical.drop(3)
        canonical.startsWith("+590") && canonical.length == 13 -> "0" + canonical.drop(4)
        canonical.startsWith("+594") && canonical.length == 13 -> "0" + canonical.drop(4)
        canonical.startsWith("+596") && canonical.length == 13 -> "0" + canonical.drop(4)
        canonical.startsWith("+262") && canonical.length == 13 -> "0" + canonical.drop(4)
        canonical.startsWith("+508") && canonical.length == 10 -> "0508" + canonical.drop(4)
        else -> null
    }

    private val PUBLIC_INTEREST = listOf(
        "01510", "02810", "03410", "04410", "05410", "09410",
        "026870", "026871", "026872", "026873", "026874",
        "026875", "026876", "026877", "026878", "026879",
        "050860",
        "059850", "059851", "059852", "059853", "059854",
        "059855", "059856", "059857", "059858", "059859",
        "059860", "059861", "059862", "059863", "059864",
        "094700", "094701", "094702", "094703", "094704",
        "094705", "094706", "094707", "094708", "094709"
    )

    /**
     * Outre-mer reserves from decision 2025-2215. 09471–09474 are allocated metropolitan
     * polyvalent ranges, not reserves, and must not be listed here.
     */
    private val RESERVED = listOf(
        "0264", "0592", "0593", "0595", "0599",
        "02680", "02681", "02682", "02683", "02684", "02685", "02686",
        "05980", "05981", "05982", "05983", "05984",
        "059865", "059866", "059867", "059868", "059869"
    )
}
