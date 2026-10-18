# Sentinel D.1 Phase 3 — design de reconstruction fidèle

## Objectif

Reprendre la branche native D.1 pour reproduire la composition et la profondeur des 13 maquettes
officielles, sans importer leurs statistiques fictives et sans casser les parcours Android existants.

## Architecture visuelle

- `SentinelVisualTokens` reste l’unique source des couleurs, rayons, typographie et états.
- Un nouveau moteur `SentinelHolographicScene` en Compose `Canvas` fournit une projection 2.5D légère
  et déterministe. Les scènes spécialisées sont `ProtectionSphere`, `ThreatGlobe`,
  `LayeredDevice`, `RiskCube`, `QuarantineChamber`, `ThreatCore`, `ContactOrbit` et `VpnGlobe`.
- Chaque scène reçoit un modèle de données minimal (`status`, libellés, preuves, points, progression)
  et ne calcule aucun verdict métier.
- `SentinelMotionPolicy` gouverne la rotation et la parallaxe. `STATIC`/économie d’énergie ne
  lancent aucune transition infinie.
- Les scènes sont décoratives pour TalkBack ; une synthèse textuelle et les actions restent dans
  les composants Compose accessibles.

## Intégration fonctionnelle

- Les routes et activités Android existantes sont conservées.
- Les références sans écran natif dédié obtiennent une route corrective explicite, avec états
  `UNKNOWN`/`LIMITED` tant qu’aucune source réelle ne permet d’affirmer le contenu.
- Aucun compteur, pays, score antivirus, menace, quarantaine ou tunnel VPN illustratif n’est
  copié dans le modèle runtime.
- Les actions critiques restent raccordées aux contrôleurs existants et sont désactivées si le
  prérequis Android ou la preuve attendue manque.

## Validation

- Tests de contrat source pour chaque écran et scène spécialisée.
- Tests unitaires des états sans preuve, des transitions de mouvement réduites et des routes.
- `testDebugUnitTest`, `compileDebugAndroidTestKotlin`, `lintDebug`, `assembleDebug` après chaque lot.
- Captures réelles uniquement si un runtime Android est disponible ; sinon la matrice marque la
  capture comme bloquée et le verdict reste `D1_BUILT_NOT_VISUALLY_VALIDATED`.
