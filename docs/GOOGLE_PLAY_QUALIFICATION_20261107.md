# Qualification Google Play — 7 novembre 2026

> Rapport historique de la candidate `3d5753ee`. La finalisation suivante intègre Doctor et prépare le laboratoire séparé : [résultats courants](../qualification/roadmap/RESULTS.md). Ne pas attribuer les artefacts ou limites de ce rapport à ce nouvel arbre Android. Les gates externes ci-dessous restent ouverts.

**Décision : NON PRÊT À SOUMETTRE.** Candidate isolée `24 / 1.0.10`, application `com.sentinel.quantum`, minSdk 24 / targetSdk 36 / compileSdk 37. Le maximum versionCode utilisé dans Play Console reste inconnu : 24 est provisoire, pas une preuve d'acceptabilité. La v22 installée n'est pas modifiée.

## Périmètre

Base v22 `fafe9f5b1c57b21f276ca62189acab625a490ec9` avec isolation des tests de la branche `fix/disposable-instrumentation-guard`, parent `6a90af97a22f862ad5524b557a34c97615fa5d96`. Ni la compression MMS expérimentale (test JPEG tronqué encore en échec), ni Doctor expérimental ne sont intégrés. Aucun changement du circuit MMS/PDU/provider, des rôles ou des données installées.

Mises à jour : version candidate, roadmap courante, fiche Play présentant explicitement Téléphone et SMS/MMS, compte optionnel bloqué avant Auth0 sans parcours de suppression configuré. L'action de suppression ouvre un navigateur vers la route HTTPS publique, sans transmettre de jeton. Restauration de session et déconnexion sont préservées. Cette protection ne remplace pas un service de suppression : aucune suppression serveur n'est prétendue implémentée.

La propriété Gradle publique `-PsentinelAccountDeletionUrl=https://votre-service/chemin-suppression` est vide par défaut. La release signée est refusée sans URL HTTPS explicite, sans identifiants/fragment, et sans configuration de signature existante. Le workflow reçoit cette URL via la variable de dépôt `SENTINEL_ACCOUNT_DELETION_URL` ; aucun workflow n'a été déclenché, aucune variable/secrète n'a été créée ou modifiée. Les domaines d'exemple servent uniquement aux tests et ne doivent pas être utilisés en production.

## Gates externes ouverts

| Gate | État et preuve requise |
|---|---|
| Identité / version Play | Compte développeur, application existante et maximum versionCode toutes pistes inconnus. Confirmer avant build signé. |
| Signature upload | Keystore d'upload non disponible dans le cloud. Vérifier son certificat public avec Play Console ; ne transmettre aucune clé. AAB unsigned interdit de dépôt. |
| Suppression de compte/données | URL et service réels non fournis. Vérifier dans l'app ET depuis le lien web public, y compris données associées, délais, rétentions légales et sous-traitants Auth0. Logout insuffisant. |
| Confidentialité | URL existante `/public/privacy.html` renvoie 403 depuis cet environnement. Accès public, identité/contact responsable, rétentions et exactitude des flux NON VÉRIFIÉS. Le 403 ne prouve pas à lui seul une panne globale. |
| Permissions restreintes | Formulaire SMS / journal d'appels : décrire les usages principaux default SMS/default Phone, fournir démonstration et attendre décision Google. Le code et le choix des rôles ne valent pas approbation. |
| Plein écran | Déclarer l'usage incoming-call du composeur par défaut et vérifier l'éligibilité / consentement sur la release. |
| Data Safety / App Access | Examiner l'AAB exact et tous les SDK : sources publiques, réputation distante opt-in, Auth0 et services conditionnels. Déclarer uniquement les flux réellement activés. Fournir accès de revue si nécessaire, sans compte pour Phone Core local. |
| Appareil / opérateur | 14 preuves Phone Core v6 sur l'artefact exact et scénarios additionnels SIM, erreurs, révocation, notifications et absence de perte de données. Tests physiques de cette candidate NON EXÉCUTÉS. |
| Tests de distribution | Test interne ; test fermé applicable notamment aux comptes personnels créés après 13/11/2023 (12 testeurs pendant 14 jours continus), puis demande d'accès production si requise. Type de compte inconnu. |
| Fiche / contenu | Captures réelles anonymisées, classification de contenu, pays, contact support, publicité et formulaire VPN le cas échéant. Aucun service VPN public n'est annoncé par cette candidate. |

## Sources officielles consultées

Récupération HTTP 200 le 07/11/2026, copies brutes conservées dans `/workspace/qualification-evidence/play-20261107` ; ce sont les exigences récupérées, pas une prédiction de politique future.

- [API cible](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en) : API 36 ou supérieure depuis le 31/08/2026 pour nouvelles apps/mises à jour mobile. API 36 est déjà suffisante ; pas de changement artificiel en 37.
- [Permissions SMS / Call Log](https://support.google.com/googleplay/android-developer/answer/10208820?hl=en).
- [Suppression compte et données](https://support.google.com/googleplay/android-developer/answer/13327111?hl=en).
- [Data Safety](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en).
- [Pages mémoire 16 Ko](https://developer.android.com/guide/practices/page-sizes) : vérifier bibliothèques ELF et packaging bundle/APK, puis exécution sur appareil compatible.
- [Tests préalables selon le compte](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en).

## Validation de cette candidate

Les commandes, résultats et hashes sont consignés dans `qualification/google-play/RESULTS.md` après exécution. Un build n'est jamais une preuve de livraison MMS ou d'acceptation Google. Les warnings et étapes non exécutées restent visibles.

## Suite et retour stable

Aucune installation autorisée ici. Préférer le canal interne Play seulement après fermeture des gates et accord d'installation explicite. Avant tout remplacement, vérifier le signataire existant, la sauvegarde autorisée et la procédure de conservation des données. Une rétrogradation de versionCode peut être refusée : ne pas désinstaller ni effacer les données pour la contourner. La v22 reste actuellement intacte ; aucun retour arrière n'est nécessaire.
