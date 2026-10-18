# Sentinel D.1 — index des références officielles

Archive source : `Sentinel_D1_13_Visuels_Officiels.zip`  
Archive SHA-256 : `b1c84e0aa8ea6186bd82d282dea59e2119b91026c2ae46de0b93751aee8c905a`

Les fichiers ci-dessous sont conservés sans conversion dans
`reference-source/visuels/`. Malgré leur extension `.png`, les 13 fichiers sont des
JPEG contenant les maquettes originales ; leurs noms et octets sont conservés.

Toutes les références ont une résolution native de `864 × 1536` pixels. La planche-contact
originale est conservée séparément en `reference-source/planche-contact.jpg` (`1150 × 1200`).

| # | Fichier original | Écran de référence | Octets | SHA-256 |
|---:|---|---|---:|---|
| 01 | `1000415621.png` | Applications à risque | 357673 | `7d1558b2fb21a0f43e55221eb535a4619c20b89c300f30db87886053fca11e47` |
| 02 | `1000415622.png` | Carte mondiale des menaces | 398833 | `02f5e8ea54fb2e10cf8c5924ccdbc449ccd93e61b6b4f63f63243baf2a672dad` |
| 03 | `1000415623.png` | Rapport d’analyse | 358287 | `8c3730276886a86254e397e06fb0b26ca769bb6daea4aea798630e8bccdb3284` |
| 04 | `1000415624.png` | Quarantaine sécurisée | 347487 | `c66d5440d8612531ad9e615c34010c65e4ffb30c8da0d692f61625daf9da48b6` |
| 05 | `1000415625.png` | Protection temps réel | 349605 | `318bd5a956168bb4d0495a6ae90aa07db2dab0da4076ac47b066e33e6817ee96` |
| 06 | `1000415626.png` | Détail menace | 328272 | `2ec17827f5edfe1755d226f59d3c0d5f5cf421d0655882c8a807325378f3c34f` |
| 07 | `1000415627.png` | Analyse de l’appareil | 326305 | `59ce27489eca5fa9990ed2c1e588cc173e5f2a6e5dcedb63416e5cb7012c32a0` |
| 08 | `1000415628.png` | Accueil / Command Center | 288684 | `3fdcb4cee989c2bbc756cb53f8e2f60cfecb4d3eaf49753a7d9fe2cde076aaac` |
| 09 | `1000415629.png` | Planche multi-écrans | 375004 | `6138e068a69d591317e191dfbaa3e5212c1f90054c42889a01e6ddfcff6da358` |
| 10 | `1000415630.png` | VPN privé | 290217 | `e881e8f49a96979d2d8c79a61dae233b27980e447002cd4f2cf1cbf4d007033c` |
| 11 | `1000415631.png` | Contacts Sentinel | 267575 | `02631e2b9ec5121f97b6bfb6db36247b178ef888dd14410b97880459678b2c4f` |
| 12 | `1000415632.png` | SMS sécurisés et phishing | 281680 | `eb85c230bccf11b7e79582ae6386c88e81f8b39aecf4cf07727ed83247bdb4ff` |
| 13 | `1000415633.png` | Fiche numéro | 280733 | `2afa7692ef93882f219fc182fbb1f0af8ff399252e2dd5a1460f13583e536143` |

## Règle de fidélité

Les références définissent la composition, la profondeur, les proportions, l’éclairage, la
hiérarchie et les interactions visuelles. Elles ne constituent pas des preuves métier. Les
compteurs, risques, connexions, scores, menaces, quarantaines et états affichés par l’application
doivent venir des adaptateurs Android réels ; à défaut, le rendu utilise `UNKNOWN`, `LIMITED`,
`LOCKED` ou `TEST` et n’affiche pas la donnée illustrative de la maquette.
