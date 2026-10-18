# Audit visuel initial — D.1 phase 3

## Constat

Les maquettes officielles sont des écrans portrait `864 × 1536` avec une composition très dense
mais hiérarchisée : un hero 3D occupant environ le premier tiers, des panneaux glass spécialisés,
des métriques courtes, des actions larges et une barre de navigation basse persistante. La profondeur
vient d’une scène dédiée par module (globe, smartphone, cube, cylindre, portrait ou menace), pas
d’un simple bouclier cyan réutilisé.

## Écarts de la branche de départ

- `SentinelHeroVisual` et `SentinelQuantumCore` offrent une base de tokens et d’orbites, mais leur
  noyau est générique et ne matérialise pas les sept compositions spécialisées demandées.
- Plusieurs écrans utilisent encore une longue colonne de `SentinelGlassCard` avec un hero commun ;
  la structure ne suit pas la densité contrôlée de la planche officielle.
- Les écrans VPN, menace, quarantaine, carte mondiale et rapport n’ont pas chacun une scène native
  dédiée.
- Les références contiennent des données illustratives qui ne doivent pas être recopiées comme
  états réels ; chaque nouveau panneau devra recevoir une valeur issue d’un contrat runtime ou
  afficher explicitement son indisponibilité.

## Décision de reconstruction

La phase 3 ajoute un moteur Canvas Compose procédural, léger et réutilisable, avec projections 2.5D
(ellipses, parallaxe, profondeur, anneaux, points et couches) spécialisées par écran. Il ne charge
aucune image de maquette dans l’application. Les scènes lisent `SentinelStatus`, la politique de
mouvement et les données runtime déjà présentes ; le mode statique conserve la hiérarchie sans
planifier de frames.

## État après le lot phase 3

- Les 13 références sont conservées et indexées ; la planche multi-écrans sert de contrôle de
  cohérence, pas de ressource d’exécution.
- Les surfaces officielles utilisent maintenant les scènes `COMMAND_CENTER`, `REAL_TIME_PROTECTION`,
  `RISK_APPLICATIONS`, `ANALYSIS_REPORT`, `DEVICE_ANALYSIS`, `VPN_GLOBE`, `CONTACT_ORBIT`,
  `SECURE_MESSAGES`, `NUMBER_IDENTITY`, `THREAT_GLOBE`, `QUARANTINE_CHAMBER` et `THREAT_DETAIL`.
- Les bandes `SentinelReferenceMetricStrip` remplacent une partie des longues cartes de synthèse et
  conservent la chaîne de preuve dans chaque valeur.
- La capture réelle reste bloquée tant qu’aucun émulateur ou appareil Android n’est attaché. Les
  écarts de pixel, de GPU, de TalkBack réel et de non-chevauchement ne sont donc pas déclarés
  validés ; voir `validation-matrix.md`.
