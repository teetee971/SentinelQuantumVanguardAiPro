# Qualification sur appareil — observations partielles du propriétaire

La version installée11/1.0.9 a été confirmée dans une capture de sortie ADB après autorisation explicite de mise à jour. Les captures suivantes montrent cinq états d'interface sur le S24+. Ce sont des preuves utilisateur, pas des actions ou captures exécutées directement depuis le cloud. Aucune capture brute, nom, numéro, texte de message ou donnée de contact n'est copié dans Git. Les originaux restent dans cette conversation ; aucun chemin local ni empreinte d'image n'est inventé.

| Étape | Observation limitée | État de la validation |
|---|---|---|
| 1 — Mise à jour | Dumpsys utilisateur : com.sentinel.quantum versionCode11/versionName1.0.9 | Version installée confirmée ; pas de garantie de préservation complète de données |
| 2 — Conversation | Des messages antérieurs sont affichés, avec statuts lus du fournisseur Android et zone de réponse | Lecture et rendu observés ; aucun nouvel envoi/livraison prouvé |
| 3 — Liste Messages | Plusieurs conversations accessibles, catégories et navigation de composition visibles | Écran ouvert ; navigation exhaustive/non-régression asynchrone non rejouées |
| 4 — Téléphone | Réglages/configuration et états locaux affichés | Ouverture observée ; les badges « Prêt » ne prouvent pas un appel ni une notification réelle |
| 5 — Contacts | Liste et fiche du même contact ouvertes, actions et avatar initiale visibles | Lecture de ces données observée ; contact avec photo, édition, révocation et préservation complète non validés |

Aucun crash n'est visible sur ces états capturés ; aucun journal runtime, test répété de lancement ou preuve générale d'absence de crash n'est fourni. L'accès à d'anciens SMS du fournisseur et à des contacts n'établit pas la conservation de toutes les préférences, favoris ou bases privées Sentinel. Les26 scénarios ne sont pas tous clos : mise à jour/ouvertures/lecture partiellement observées, appels, transport, notifications, révocations et cycle de vie encore à exécuter.

## Revue visuelle limitée aux images fournies

Les références officielles R02, R03 et le collage C01 sont celles déjà fournies par l'utilisateur (registre VISUAL_REFERENCES.md). Les captures actuelles peuvent être comparées qualitativement à ces visuels, pas comme un test automatisé ou un audit interactif complet.

- Messages : cartes hautes et bouton de suppression répété sous chaque message ; hiérarchie et densité éloignées des bulles/compositeur de R02. En-tête compact présent mais composition holographique/identité de la maquette non reproduites exactement. Ne pas inventer de chiffrement, confiance ou analyse distante pour copier ses libellés.
- Contacts : fiche dans une feuille modale avec globe décoratif puis carte et initiale séparée ; composition différente du portrait central de R03. Aucune photo d'un autre contact n'est démontrée sur ces captures ; une initiale seule ne prouve pas un échec de chargement, puisqu'une photo source peut être absente.
- Palette : surfaces/boutons violets visibles. Color.kt ne définit pas primaryContainer/secondaryContainer et plusieurs surfaceContainer ; les composants utilisent ces rôles Material, dont des valeurs par défaut. La palette cyan des accents seule ne couvre donc pas tous les conteneurs. C'est une cause source identifiée à traiter, pas un correctif déjà livré.
- La feuille de contact recouvre la liste dans un état et est davantage déployée dans l'autre. Les captures seules ne prouvent pas une impossibilité de faire défiler ou d'atteindre une action ; tester le geste, les grands caractères et TalkBack avant de déclarer un blocage.

**Conformité visuelle D.1 complète : NON VALIDÉE.** Mesure des contrastes, accessibilité, focus, défilement interactif et lecture à voix haute NON EXÉCUTÉS. Aucun nouveau build, APK, changement de thème, interaction ou données dans cette collecte de preuves.

Prochaine étape recommandée : appel entrant depuis une ligne de test connue, réponse via Sentinel, raccrochage et disparition de la notification ; preuves expurgées et observations du correspondant. Aucun appel automatisé, numéro inconnu ou surtaxé demandé.
