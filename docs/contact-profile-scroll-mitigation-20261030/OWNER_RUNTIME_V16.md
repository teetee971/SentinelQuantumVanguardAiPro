# Retour physique propriétaire — candidat 16

Le propriétaire a exécuté le bloc de signature locale du candidat16. La sortie transmise indique le certificat historique `68915958f2f8683afead51cc3a1e578d182df4b9d9b2505c48c261b1a17b3fe2`, package `com.sentinel.quantum`, versionCode16/versionName1.0.9, et le marqueur final de contrôles signature/intégrité réussis, sans installation par le script.

SHA256 du fichier signé local, selon cette sortie : `7362e785d0d325d2a843784875a2d6e15dd11d2e017c83d30e8edf66629477e5`. Aucun APK signé ni clé privée reçu dans le cloud. Le fichier unsigned contrôlé sur le téléphone correspond à `c74f72491552df4a9ae1009c769dc54927d4c8334d5adb0963142a3561bf43f6`, source `0e5df74e482f91a1fe3048385dfcd161ad93fb14`.

Après autorisation explicite de mise à jour manuelle, le propriétaire rapporte de nouveau un gel au bas de la fiche Contacts. Les captures montrent les actions de recherche web et les profils liés présents dans le candidat16. Pas de nouvelle sortie dumpsys/package ni de contrôle binaire de l'application installée : attribution à la mise à jour rapportée, pas preuve indépendante de version installée.

**Verdict du parcours de défilement : FAIL SIGNALÉ.** La mitigation13 (overscroll du contenu désactivé), conservée dans16, ne suffit pas selon cet essai. Aucune résolution du gel, aucun ANR ou blocage du thread principal démontré par les captures seules. Aucune donnée personnelle des captures reproduite ici.

## Investigation suivante

- Le contenu utilise verticalScroll avec overscrollEffect=null, dans ModalBottomSheet dont les gestes restent activés par défaut.
- Les sources Material3 déjà examinées montrent un nestedScroll du panneau, consommation de deltas et traitement des flings résiduels. Ce mécanisme demeure actif même sans overscroll du contenu. Son existence ne prouve pas qu'il cause le gel S24+.
- Le test instrumenté existant repeatedBottomGesturesStillAllowScrollingBackAndClosing couvre gestes répétés au bord, remontée puis fermeture. Il n'a pas été exécuté. Il ne distingue pas encore une fermeture immédiatement pendant le gel d'une fermeture après remontée.
- Question de diagnostic posée : Fermer répond-il immédiatement pendant le gel ? Réponse en attente. Ce résultat permet de distinguer une interception/animation du défilement d'un problème plus général de réactivité, sans suffire seul à établir une cause.
- Aucun appareil ADB disponible dans l'environnement cloud lors de cette investigation. Tests instrumentés NON EXÉCUTÉS. Aucun nouvel APK, changement de gestes, installation, désinstallation ou effacement effectué pour ce retour.

Les tests unitaires précédents ne valident pas cette interaction tactile physique. Projet et défilement restent NOT READY pour une déclaration de conformité complète.
