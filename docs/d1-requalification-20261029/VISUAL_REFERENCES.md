# Registre vérifiable des références D.1

## Originaux fournis par l’utilisateur

Emplacement des originaux : pièces jointes de **cette conversation**, dans les trois messages décrits ci-dessous, avant « Vérifie et corriger ». Ils ont été vus dans la conversation. Ils ne disposent pas d’un fichier local matérialisé ni d’un lien Drive/Figma observé dans cette reprise : aucun chemin, URL ou SHA-256 d’original n’est inventé. Conserver ces pièces jointes avec la conversation ; leur export ultérieur permettra de leur attribuer une empreinte. Les images individuelles affichées sont au format 720 × 1280 ; le collage contient douze vues. Le décompte historique « 13 » ne désigne pas un jeu local de treize fichiers identifié.

| Lot / emplacement précis dans la conversation | Pièce | Référence / vue |
|---|---:|---|
| A — message « J ai les autres design d.1 officielle » | 1 | R01 Fiche numéro inconnu |
| A | 2 | R02 Messages sécurisés, conversation et analyse de liens |
| A | 3 | R03 Fiche contact Marie Dubois |
| A | 4 | R04 VPN privé |
| A | 5 | C01 Collage douze vues : accueil, appel entrant, appel en cours, liste Messages, analyse appareil, protection temps réel, Network Center, liste Contacts, analyse système, carte des menaces, détail menace, scan intelligent |
| B — message suivant composé de cinq images, après le lot A | 1 | R05 Applications à risque |
| B | 2 | R06 Carte des menaces |
| B | 3 | R07 Rapport d’analyse |
| B | 4 | R08 Quarantaine sécurisée |
| B | 5 | R09 Protection temps réel |
| C — message suivant composé de cinq images, juste avant « Vérifie et corriger » | 1 | R10 Menace détectée |
| C | 2 | R11 Analyse de l’appareil |
| C | 3 | R12 Accueil Quantum Vanguard AI Pro |
| C | 4 | Doublon C01 |
| C | 5 | Doublon R04 |

Les vues du collage et les vues détaillées peuvent représenter le même parcours dans des compositions différentes. Les doublons ci-dessus ne sont pas comptés comme de nouvelles maquettes.

## Comparaison structurelle et limites

| Surface cible | Référence | État des sources / écart restant |
|---|---|---|
| Téléphone et appels | C01 colonnes appel entrant/en cours ; R01 pour contexte numéro | En-tête D.1 illustré et saisie flexible intégrés au téléphone. L’écran d’appel et l’analyse d’identité ne sont pas déclarés identiques aux compositions illustrées ; pas de score/confiance factice. Appels S24+ à requalifier. |
| Contacts | C01 liste Contacts ; R03 fiche | Liste locale avec avatar réel et accès à une fiche, coordonnées, appel/message, édition déléguée et partage explicite. Absents : profils sociaux vérifiés, notes enrichies, anniversaire et provenance par champ. Aucun badge « identité fiable » inventé. |
| Messages | C01 liste ; R02 conversation | En-tête compact et panneau de risque local intégrés. Bulles et parcours provider existants préservés. Raccourcis Appeler/Ajouter intégrés à l’en-tête en ouvrant les parcours Android existants, sans opération automatique ; ouverture réelle à tester. Géographie, âge du domaine et analyse distante ne sont pas inventés. Fidélité du rendu et SMS/MMS opérateur non validés. |
| Navigation/Bluetooth | C01 Network Center, navigation commune | Barre de scan séparée du contenu via Scaffold. Pas de nouvelle disponibilité BLE revendiquée. Défilement, retour, police agrandie à tester. |
| Accueil/VPN | R12/R04 et C01 | Sources existantes conservées. Les états de connexion/protection de la maquette ne sont pas une preuve de tunnel ou protection active. Pas de refonte exhaustive revendiquée. |
| Sécurité avancée | R05–R11 et vues C01 | Inventaire/analyses locales présents selon la matrice des sources. Aucune preuve d’antivirus exhaustif, isolation système, quarantaine générale ni réseau mondial temps réel. Les chiffres et capacités illustrés ne sont pas ajoutés comme données réelles. |

**Comparaison visuelle du rendu : NON EXÉCUTÉE.** Aucune capture exploitable de l’application candidate n’a été obtenue. Les images de référence et les illustrations ne remplacent pas les captures attendues sur S24+.

## Décorations intégrées (générées, pas originales officielles)

Ces deux illustrations sans texte ni indicateur de service sont inspirées de la direction visuelle reçue. Leur rôle est décoratif ; elles ne prouvent pas la conformité D.1.

| Fichier local conservé | Ressource du dépôt | SHA-256 |
|---|---|---|
| `/workspace/d1-assets/d1-orbital-globe.png` | `native-android-app/app/src/main/res/drawable-nodpi/d1_orbital_globe.png` | `1d13c77349f26e7e470c9a19b017ec1211ba7ca4b39392eef0cc07a7765df990` |
| `/workspace/d1-assets/d1-unknown-identity.png` | `native-android-app/app/src/main/res/drawable-nodpi/d1_unknown_identity.png` | `b00461588d257184d0a2a4bb31f18d2ffcbf5604e53dad12b533a8fcfd756ad2` |

Les métadonnées locales sont conservées dans `/workspace/d1-assets/ASSET_METADATA.json`, copiées ici dans `ASSET_METADATA.json`. Les ressources sont appelées par `SentinelCommunicationHeader`, lui-même utilisé dans les parcours Téléphone, Contacts, fiche contact et Messages. Les callsites et ressources du paquet sont recensés dans les preuves de livraison.
