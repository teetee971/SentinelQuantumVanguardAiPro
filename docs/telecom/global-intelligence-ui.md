# Global Telecom Intelligence — D.1 screen mapping

The native route `global_telecom_intelligence` is rendered by
`GlobalTelecomIntelligenceScreen`.

| D.1 reference intent | Android component | Runtime truth |
|---|---|---|
| Globe mondial / anneaux | `SentinelHolographicScene(THREAT_GLOBE)` | Nodes are registry entries; no fabricated global threat points |
| Dashboard de sources | `SentinelReferenceMetricStrip` | Counts derive from the registry/cache; active connectors stay `0` until observed |
| Recherche par indicatif | `InternationalNumberResolver` | E.164/`00` supported; national context required; shared codes show `AMBIGUOUS` |
| Provenance | Source cards and status chips | Official URL, observation state and digest only when a probe returned one |
| Action de synchronisation | `OkHttpOfficialSourceTransport` through `TelecomSourceSyncCoordinator` | Explicit ARCEP probe only; errors show unavailable and retain last good evidence |
| Phone Core hand-off | Navigation to `CallBlockingScreen` | User-owned rules remain the only block authority |

The screen uses the existing D.1 dark glass system and the same reduced-motion
policy as the threat-map surface. The visual globe is a native Canvas scene;
it is not a screenshot or a remote image.
