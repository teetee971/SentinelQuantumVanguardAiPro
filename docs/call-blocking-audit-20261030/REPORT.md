# Filtrage — candidat16, préparation et état UI

**Appels traversants signalés : cause NON ÉTABLIE / NON RÉSOLUS par preuve physique. NOT READY pour déclarer le filtrage fiable sur S24+.** Le propriétaire se rappelle de quelques appels passés mais pas des numéros/formats ni de l’état du processus. Aucun exemple d’échec, trace, rappel ou appel de test n’est fourni. Ne pas attribuer ces incidents au démarrage froid, aux formats ou au rôle sans preuve.

## Corrections concrètes

- SentinelDialerActivity.onPostResume demande maintenant launchForegroundMaintenance, comme MainActivity le faisait déjà. L’entrée directe Téléphone/Contacts ne préparait pas ce cache quand l’accueil n’avait pas été ouvert. Maintenance existante, asynchrone/idempotente, après reprise de l’UI ; comprend les réparations locales SMS/MMS et la préparation du snapshot/clés préexistantes. Aucun nouvel envoi/rôle automatique.
- CallBlockingScreen relit sélection Android et permission Contacts à chaque RESUMED ; snapshot chargé sur IO, lecture annulée à la pause. Le badge dit **Sentinel choisi pour filtrer**, pas « tous les appels sont bloqués ». Un retour Paramètres hors launcher n’a plus à laisser la sélection affichée périmée.
- Une course introduite par cette nouvelle lecture a été relevée en revue **avant publication** : une lecture ancienne pouvait masquer un ajout ou restaurer visuellement des règles retirées. CallBlockingUiSnapshot protège par révision ; toutes les publications d’édition (exactes, préfixes, ARCEP, synchronisation) incrémentent celle-ci. Trois tests de comportement couvrent ajout, retrait et rafraîchissement accepté sans édition ; pas de test miroir ajouté pour la simple délégation de lifecycle.
- Saisie numéro/préfixe conservée si ajout échoue ; message numéro mentionne format/limite/stockage. Précision sur règle exacte, numéros masqués et danger d’un préfixe trop large.

## Ce qui ne change pas

**Le premier appel d’un processus froid peut encore passer avant préparation des caches**, de même si les clés/règles ne sont pas disponibles. Le filtrage reste volontairement fail-open dans ces cas. Aucune préparation disque/Keystore ajoutée à Application.onCreate ou onScreenCall ; réponse Telecom et protections d’urgence conservées. Cette version réduit la fenêtre après entrée UI directe, pas le cold start Telecom sans UI. Le rôle attribué ne prouve pas un rejet effectif.

Moteur, normalisation globale, formats des empreintes, règles persistées, clés HMAC, service de screening et réponse Android **inchangés**. Pas de migration d’anciennes empreintes ni de pays/indicatif deviné. Les formes FR national/+33/0033 restent couvertes par les tests existants ; autre région nécessitant E164 explicite à vérifier selon le cas. Numéro masqué, numéro différent d’une règle exacte, règle expirée, rôle absent et classification urgence inconnue ont des traitements distincts, sans prétendre que l’un explique les appels signalés.

Aucun blocage natif BlockedNumberProvider ou blocage SMS ajouté. Aucune règle ni contact supprimé par le correctif, aucune nouvelle clé/secret, aucun schéma Room changé. L’échec de persistance existante n’est pas prétendu réparé : le store SharedPreferences et ses limites restent inchangés. Nombre affiché/rôle ne doivent pas être considérés comme un test d’appel réussi.

## Source et validations

- Dépôt teetee971/SentinelQuantumVanguardAiPro ; branche fix/d1-phone-messages-conformity-20261029 ; PR brouillon1748.
- **Source des APK16 : 0e5df74e482f91a1fe3048385dfcd161ad93fb14** ; base 4bce5eccfab78b3e348fc3e3a0ed0f4bf302f5c6 ; fichiers exacts dans SOURCE_FILES.txt. Les commits ultérieurs de livraison sont documentaires.
- VersionCode16/versionName1.0.9, packagecom.sentinel.quantum, min24/target36/compile37.
- Build Kotlin/app/tests/APK debug/test/unsigned exit0,1m38 ; post-commit exit0,16s,tâches incrémentales sur mêmes sources. Avertissements Gradle/dépréciations conservés dans journaux.
- **App :1440réussis,0échec,0erreur,0ignoré**,308XML ;3nouveaux tests de publication UI. La commande ciblée précédente moteur/expiration/bornes exit0 porte sur les20cas des trois classes inchangées, sans radio ni appareil. Les gardes de source de démarrage/réponse sont des tests structuraux existants, pas des mesures physiques de délai.
- **Wearable26/26**,0échec/erreur/ignoré, inchangé/caché. **Node produit69/69 + livraison15/15**,0échec/ignoré.
- **Tests instrumentés NON EXÉCUTÉS. Trajets onPostResume/Paramètres et rejet d’appel physique NON EXÉCUTÉS.** APK de tests compilé, aucune nouvelle instrumentation de radio revendiquée. Pas de RED/GREEN des appels signalés. RED d’API du helper UI absent puis GREEN de ses3tests et suite complète ; revue statique ciblée, P2 de publication traité avant livraison.
- Room non suivi préservé ;1.json SHA256 a7c5b4e1926c4441629734c0703ba6174e6bcf3796394b51302846bac0dae0c4. Aucun merge, workflow payant, installation automatique, numéro réel recherché/appelé ou clé exportée.

## Artifacts

- **unsigned** `Sentinel-D1-v16-UNSIGNED-0e5df74e.apk` — 102511998octets, SHA256 `c74f72491552df4a9ae1009c769dc54927d4c8334d5adb0963142a3561bf43f6` ; apksigner exit1, zipalign4 exit0, zipalign16KiB exit0, aapt exit0.
- **cloud_debug** `Sentinel-D1-v16-CLOUD-debug-0e5df74e.apk` — 102615024octets, SHA256 `4a3ad04633cf23cef7afc1d806e1d455a349c09b311345c187c3c864e04e55cd` ; apksigner exit0, zipalign4 exit0, zipalign16KiB exit0, aapt exit0.
- **instrumentation** `Sentinel-D1-v16-androidTest-0e5df74e.apk` — 1239931octets, SHA256 `16ae14b9c72da03f91518f88c6eab3962c645348f0b06fd11f77c735e0561146` ; apksigner exit0, zipalign4 exit0, zipalign16KiB exit0, aapt exit0.

[APK16 NON SIGNÉ — NON INSTALLABLE](https://drive.google.com/file/d/1CcQvo-0J9Gc-hbBNc6bJOxNaMz1PBa1p/view?usp=drivesdk)

Exit1 de vérification unsigned attendu. Cloud debug/test : certificatdee0d0f14e3a211d5ce9adca0280acde443e2312857d33da2258bef0f1b632e1, incompatible avec propriétaire68915958f2f8683afead51cc3a1e578d182df4b9d9b2505c48c261b1a17b3fe2. La clé originale demeure sur le téléphone. Signature locale, contrôles de contenu/package/code16/alignement/certificat/SHA puis autorisation avant mise à jour, sans désinstaller ni effacer. Aucun APK16 signé propriétaire disponible dans le cloud. SHA APK local ; taille/métadonnées Drive relues, pas de hash binaire distant intégral revendiqué.

## Qualification restante

1. Sur appareil après signature/mise à jour autorisée : ouverture directe Téléphone/Contacts sans accueil, préparation en avant-plan, retour Paramètres et retrait/réattribution du filtrage/Contacts ; aucun badge périmé, compteur ne perd pas une édition pendant refresh. Ne modifier les rôles qu’en présence du propriétaire et hors appel actif.
2. Avec une seconde ligne connue/autorisée lorsqu’elle sera disponible : règle exacte, formes usuelles, préfixe précis, reprise, cache chaud et processus froid, journal de décision expurgé. Ne pas appeler des numéros inconnus/suspects pour tester et ne pas supprimer les règles pour contourner un problème.
3. En cas de prochain appel traversant spontané : relever seulement heure, présence réelle dans la liste exacte/préfixe, forme masquée/nationale/internationale et état UI/app ; garder numéro complet privé. Aucun souvenir de détail exigé pour l’incident passé.
4. Une amélioration par blocage système Android pourrait être étudiée séparément pour les numéros exacts : droits disponibles, portée possible appels/SMS, règles déjà créées par d’autres apps et suppression ciblée nécessitent un contrat explicite. **Non implémentée dans16**, aucune garantie donnée.

Le gel de fiche Contacts reste ouvert, mitigation13 conservée ; les parcours sociaux14 et recherche web15 sont conservés sans collecte API automatique. Les26scénarios S24+, fidélité D.1 et Voice gardent leurs limites. Une comparaison avec13 pour isoler le rebond doit se faire avant une version supérieure ou sur un autre appareil : aucun downgrade/desinstallation/effacement comme méthode de test.
