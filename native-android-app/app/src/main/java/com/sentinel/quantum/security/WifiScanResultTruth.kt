package com.sentinel.quantum.security

/** Pure presentation contract for the provenance of Android Wi-Fi scan results. */
object WifiScanResultTruth {
    enum class Source {
        FRESH,
        CACHED_SCAN_REJECTED,
        CACHED_PLATFORM_STALE,
        CACHED_TIMEOUT
    }

    fun statusMessage(source: Source, networkCount: Int): String? = when (source) {
        Source.FRESH ->
            if (networkCount == 0) "Scan actualisé : aucun réseau visible." else null
        Source.CACHED_SCAN_REJECTED ->
            if (networkCount > 0)
                "Android a limité ou refusé le nouveau scan. Les derniers résultats disponibles sont affichés."
            else
                "Android a limité ou refusé le nouveau scan et aucun résultat précédent n’est disponible."
        Source.CACHED_PLATFORM_STALE ->
            if (networkCount > 0)
                "Android indique que le scan n’a pas été actualisé. Les derniers résultats disponibles sont affichés."
            else
                "Android indique que le scan n’a pas été actualisé et aucun résultat précédent n’est disponible."
        Source.CACHED_TIMEOUT ->
            if (networkCount > 0)
                "Android n’a pas confirmé le scan dans le délai prévu. Les derniers résultats disponibles sont affichés."
            else
                "Android n’a pas confirmé le scan dans le délai prévu et aucun résultat précédent n’est disponible."
    }
}
