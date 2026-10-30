# Candidat17 — isoler les gestes du panneau Contacts

Le propriétaire confirme que Fermer répond immédiatement pendant le gel en bas de fiche, après mise à jour16 rapportée. Ceci rend moins probable une indisponibilité globale de l'UI au moment du toucher ; cela ne constitue pas une trace ANR ni une preuve de cause. La désactivation du rebond du contenu (13–16) n'a pas suffi.

Les sources Material3 examinées montrent que ModalBottomSheet active un nestedScroll et draggable distincts du verticalScroll de son contenu. sheetGesturesEnabled=false supprime ces deux gestionnaires du panneau. Hypothèse : leur interaction participe au blocage observé au bord du contenu. Pas de reproduction cloud disponible pour confirmer cette hypothèse.

Modification fonctionnelle unique : sheetGesturesEnabled=false dans le vrai D1ContactProfileFrame. Effet attendu : le panneau ne peut plus être déplacé/fermé en le glissant. Fermer, Retour et défilement interne conservés. Pas de suppression de données/champs/sélection de texte, pas de nouvelle dépendance, de permission, de stockage ni de modification Téléphone/Messages. versionCode17 permet une mise à jour normale depuis16 après signature historique locale.

Tests : contrôle tactile de fermeture au bord ajouté avant cette modification au commit a9a8f4cd ; nouveau contrôle draggingProfileHeaderDoesNotMoveOrDismissSheet écrit avant le paramètre de production. Celui-ci injecte un glissement sur l'en-tête, vérifie visibilité/position puis fermeture tactile. Exécution instrumentée NON EXÉCUTÉE : ni RED ni GREEN runtime revendiqués. La compilation et les tests JVM ne reproduisent pas une interaction tactile Samsung.

Validation physique ciblée après signature et installation explicitement autorisée : bas de fiche, attendre, trois gestes supplémentaires, remontée immédiate, Fermer directement au bord, Retour, réouverture, texte agrandi. Vérifier également que la perte volontaire du glissement du panneau ne gêne pas la navigation. Si le gel persiste, recueillir des traces expurgées et réexaminer la piste ; ne pas empiler de changements de gestes sans preuve.

Verdict avant essai : mitigation expérimentale, NOT READY pour déclarer le gel corrigé ou la conformité D.1. Pas d'installation automatique ; même clé historique requise ; aucun downgrade, désinstallation ou effacement de données.
