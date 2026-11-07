# Feuille de route réaliste — Sentinel

Révision : 7 novembre 2026. **Publication Google Play : BLOQUÉE**, pas de déclaration « Production Ready ».

La référence détaillée reste [docs/ROADMAP.md](docs/ROADMAP.md), les états produit [config/product-capabilities.json](config/product-capabilities.json). Le dossier courant est [docs/GOOGLE_PLAY_QUALIFICATION_20261107.md](docs/GOOGLE_PLAY_QUALIFICATION_20261107.md). Les anciennes références PR #1435/#1497 et Phone Core v5 ne décrivent plus cette candidate.

1. **Candidate isolée** : préserver la v22 installée ; candidate 24 / 1.0.10 issue de la v22 et des tests isolés. Confirmer le maximum versionCode dans toutes les pistes Play avant dépôt. Le nom du dossier ou un build debug ne prouve pas la version publiée.
2. **Compte et vie privée** : fournir le parcours HTTPS réel de suppression de compte/données et l'URL publique de confidentialité avec contact responsable et rétentions. La connexion optionnelle est bloquée sans route configurée ; la déconnexion et Phone Core local restent accessibles. Un lien configuré ne prouve pas l'exécution de la suppression serveur.
3. **Qualification technique exacte** : tests JVM, manifeste fusionné release, lint, AndroidTest compilé, AAB et bibliothèques natives 16 Ko. Conserver commandes, résultats, SHA et limites. Aucun test opérateur n'est déduit d'un build.
4. **Acceptation Phone Core v6** : exactement 14 preuves sur l'installation courante, plus double-SIM, notifications, révocation et erreurs. Les tests destructifs ne doivent pas tourner sur le S24+ stable. Les observations v22 historiques ne deviennent pas des preuves de la candidate.
5. **Play Console** : signature d'upload existante, versionCode inédit, déclarations SMS/journal d'appels et plein écran, Data Safety, App Access, questionnaire de contenu, captures exactes, tests internes puis fermés selon le compte. Le compte développeur et l'accord Google ne sont pas vérifiables par le dépôt.
6. **MMS JPEG v23** : conserver à part jusqu'à correction du test de JPEG tronqué accepté par le décodeur Android. Ne pas intégrer ce défaut connu dans la candidate Play. La v22 conserve son refus des MMS trop volumineux.
7. **Nettoyeur Doctor** : intégré à la candidate de finalisation, avec lecture de dossiers en flux, copies reconnues de plus de 24 h, sélection et confirmation. Nettoyage borné disponible à partir d’Android 8 ; fonctions Phone Core inchangées sur Android 7. Qualification UI physique encore NON EXÉCUTÉE ; laboratoire séparé `.labpreview` prévu pour la réaliser sans remplacer la v22.
8. **Services distants** : VPN, LiveKit/PSTN, réputation communautaire et autres fonctions conditionnelles restent indisponibles tant que configuration, backend, consentement, autorisation et tests réels manquent. Pas de refonte ni de mise à jour de dépendances sans défaut démontré.

La clôture requiert une preuve datée liée au SHA, à l'environnement et à l'artefact. Une étape non exécutée reste NON EXÉCUTÉE ; un blocage reste ouvert. Ni fusion dans main, ni publication, ni installation de cette candidate sur la v22 stable.

Le suivi d’exécution de cette itération est [qualification/roadmap/RESULTS.md](qualification/roadmap/RESULTS.md). Les actions nécessitant le propriétaire et leurs preuves de clôture sont détaillées dans [OWNER_HANDOFF.md](qualification/roadmap/OWNER_HANDOFF.md). Aucun jalon externe n’est fermé par cette mise à jour.
