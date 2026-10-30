# Sentinel D.1 — complément de revue statique

Révision examinée : c322631415c1ecc95d0e3a43114b39aaeb9ce1b8.
Branche : fix/d1-phone-messages-conformity-20261029.
PR : https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1748.
Comparaison : 92d1c916ebbdf393166fcf2fd904894ebee15661..c322631415c1ecc95d0e3a43114b39aaeb9ce1b8.

Aucune modification du code, compilation, exécution de test, installation ou fusion n’a été effectuée pour cette revue. Les résultats précédents restent des preuves de leur révision, pas une nouvelle validation physique. Les schémas Room non suivis sont toujours présents.

## R1 — P2 : une photo mise à jour à URI inchangée peut rester obsolète

Sources vérifiées :
- native-android-app/app/src/main/java/com/sentinel/quantum/ui/design/D1ContactProfile.kt:36 : produceState ne redémarre que si contactId ou safeUri changent.
- native-android-app/app/src/main/java/com/sentinel/quantum/security/LocalContactLookup.kt:43 : projection de la photo sans CONTACT_LAST_UPDATED_TIMESTAMP ni autre révision de contenu.
- native-android-app/app/src/main/java/com/sentinel/quantum/SentinelDialerActivity.kt:951 : le retour dans l’Activity recharge le répertoire, mais ne change pas les clés du producteur photo lorsque identifiant et URI restent égaux.

Cas : laisser une ligne de contact composée ; modifier sa photo via l’éditeur Android ; revenir. Si le fournisseur conserve la même URI pour les nouveaux octets, le producteur conserve ses clés et ne relit pas l’image. Le filtrage par contactId + URI évite le mélange entre contacts, mais n’invalide pas un ancien contenu sous la même URI. Il ne suffit donc pas d’annoncer « photos corrigées » sans cette distinction.

Statut de preuve : défaut d’invalidation déduit des sources et de la sémantique des clés de produceState ; NON REPRODUIT sur S24+. Le fournisseur n’a pas été interrogé dans cette reprise pour observer s’il conserve une URI dans ce scénario.

Correction recommandée pour l’itération suivante : transporter une révision de contact/photo provenant du fournisseur (CONTACT_LAST_UPDATED_TIMESTAMP), l’inclure dans l’identité du chargement et du résultat affichable ; prévoir l’invalidation après retour de l’éditeur si la révision n’est pas disponible. Préserver la restriction au fournisseur local et les bornes de lecture/décodage. Aucun changement SQLite/Room n’est nécessaire.

Validation attendue : même contact et même URI, contenu/révision modifiés ; l’ancien résultat ne doit plus être affichable. Compléter avec un test de recomposition à chargement retardé et un essai S24+ de modification de photo. Aucune correction n’est prétendue appliquée dans ce complément.

## R2 — couverture : les tests photo JVM ne prouvent pas le comportement Compose

ContactPhotoPolicyTest contrôle l’acceptation des URI et le filtrage d’un résultat déjà chargé par identifiant/URI. Il ne monte pas D1ContactAvatar et ne simule pas une réponse différée du fournisseur après recyclage de ligne. Le test de layout instrumenté contrôle un en-tête long ; il ne couvre pas ce chargement photo et n’a pas été exécuté sur runtime.

Cela ne contredit pas les 1366 tests unitaires réussis. Cela limite ce que ce total démontre. Ajouter un test de recomposition/chargement différé couvrant aussi le changement de révision, puis exécuter sur runtime disponible.

## Contrôles statiques complémentaires

- Les en-têtes D.1 et la fiche sont effectivement appelés par les Activities Téléphone/Contacts/Messages, et les deux ressources sont présentes dans les preuves aapt2.
- Les actions d’édition/partage restent explicites ; les appels réutilisent le contrôle de rôle et la sanitation du numéro.
- La barre Scanner est réservée par Scaffold ; aucun constat visuel d’absence de chevauchement sur S24+ n’est ajouté.
- Aucun faux pays, âge de domaine, profil social vérifié ou statut de service opérationnel ajouté dans les correctifs examinés.
- Aucune capture de rendu D.1 exploitable, aucune conformité complète ni absence de crash au lancement démontrée par cette revue.

## Suites distinctes encore requises

1. Traiter R1 et ajouter une régression couvrant R2.
2. Après audit des changements, relancer la suite et produire un nouveau candidat avec sa propre provenance ; ne pas attribuer les résultats du candidat 78653c57 à un futur correctif.
3. Galaxy S24+ : photos après édition/recherche, parcours Contacts/Messages/Téléphone, permissions/rôles, appels simples/simultanés, notifications et SMS/MMS.
4. Comparer des captures réelles aux maquettes reçues. Voice503 et les capacités avancées non développées restent les limites du rapport initial ; aucune nouvelle sonde de service n’a été exécutée pour ce complément.
