> Mise à jour Contacts : le [correctif12](../contact-profile-completion-20261030/REPORT.md) remplace localement la fiche (photo intégrée, détails, ouverture étendue, palette locale). Aucun rendu physique de ce correctif encore fourni ; écarts des autres écrans et conformité D.1 restent ouverts.

> Depuis ce rapport initial, le propriétaire a fourni des captures réelles de Messages, Téléphone et Contacts. Une revue qualitative et les écarts visibles sont consignés dans [OWNER_RUNTIME_OBSERVATIONS.md](../signature-recovery-20261030/OWNER_RUNTIME_OBSERVATIONS.md). La comparaison complète et l’accessibilité restent non validées. Les phrases suivantes décrivent la situation initiale avant ces captures.

# Écarts visuels D.1 — séparés des tests fonctionnels

Statut global : NON VALIDÉ. Aucune capture runtime du candidat, aucune comparaison de rendu terminée. Les maquettes reçues sont indexées dans VISUAL_REFERENCES.md : originaux dans les pièces jointes de conversation, aucun jeu local de « 13 fichiers » ni empreinte d'originaux inventé.

| Surface prioritaire | Implémentation présente | Écart / preuve manquante |
|---|---|---|
| Téléphone / appels du collage C01 | En-tête de communication et contrôles Telecom réels | Comparaison entrant/sortant, clavier, multi-appel et états verrouillés non exécutée ; pas de revendication de rendu 3D identique |
| Contacts R03 / collage | Photo locale liée à l'identité, fiche et actions explicites | Parcours réel à capturer ; disposition différente du profil riche illustré ; informations sociales/confiance non fabriquées |
| Messages R02 / collage | En-tête, conversations, bulles, analyse locale et actions numéro | Actions adaptatives sous l'identité au lieu du positionnement exact R02 ; pas d'analyse réseau/domain-age ni de statut sécurisé ajouté sans preuve |
| Autres R01, R04–R12 | Modules existants préservés | Compositions et capacités avancées illustrées non qualifiées ; voir UNIMPLEMENTED_FEATURES.md et matrice existante |

Ressources décoratives locales générées : native-android-app/app/src/main/res/drawable-nodpi/d1_orbital_globe.png et d1_unknown_identity.png ; métadonnées/empreintes dans ASSET_METADATA.json et VISUAL_REFERENCES.md. Ce ne sont ni les originaux officiels ni des captures d'application.

La fidélité exigera des captures S24+ par état, annotées avec leur référence, taille de police et limitations. Les chiffres, scores et identités d'exemple des maquettes ne doivent pas être injectés comme faits de production pour ressembler au visuel.
