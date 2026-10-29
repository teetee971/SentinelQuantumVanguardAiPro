# Récents : cache lié au rôle Téléphone et à READ_CALL_LOG

## Défaut établi dans le code précédent

SystemCallLogReader vérifiait le rôle et la permission avant la lecture, mais SentinelDialerActivity conservait indépendamment recentItems. L'affichage ne vérifiait que callLogPermissionGranted, et le callback publiait inconditionnellement le résultat IO. Perdre ROLE_DIALER en conservant la permission pouvait laisser les noms/numéros en cache affichés ; un résultat lancé avant révocation pouvait aussi réapparaître après coup. Ce diagnostic repose sur le chemin de code, pas sur une reproduction physique Samsung.

## Correctif raccordé

CallHistoryReadState détient une photographie mémoire et une génération de lecture. Le rôle ET la permission sont requis, et l’Activity doit être RESUMED. Toute transition d'accès vide les lignes, réinitialise le chargement et invalide la génération précédente ; la réattribution ne restaure pas l'ancien cache. beginRead ignore un double démarrage ; complete refuse une génération ancienne ou une publication hors chargement et prend une copie des lignes acceptées.

Dans l'Activity réelle : ON_PAUSE invalide le cache et les lectures ; ON_RESUME, observateur PhoneCoreLiveRefresh, changement de permission et refreshRecents mettent à jour l'accès. Après la lecture IO, callLog.canRead vérifie à nouveau l'état Android réel avant publication. L'écran utilise le même état pour les lignes, le résumé et l'indicateur ; il explique séparément le rôle ou la permission manquants. Aucun nouveau rôle, permission ou stockage n'est créé ; aucune écriture dans le journal Android.

L'invalidation s'applique lorsque le changement est observé. En premier plan le flux existant échantillonne toutes les trois secondes et n'émet que les faits modifiés ; au retour, ON_RESUME vérifie de nouveau. Le correctif ne prétend pas recevoir une notification instantanée de toute révocation. Une requête IO déjà lancée peut se terminer mais son ancien résultat est abandonné. Le scope reste celui de la composition, annulé à sa destruction. Une transition de rôle entièrement entre deux échantillons pendant que Sentinel reste RESUMED peut échapper à l’observation ; aucun listener système supplémentaire n’est ajouté ici. La sortie du premier plan invalide indépendamment les lectures, même si le rôle est réattribué avant le retour.

## Tests et limites

Sept tests JVM : sortie du premier plan suivie d’un retour sans révocation observée, absence d'accès si l'un des prérequis manque, purge après révocation, ancien résultat rejeté après révocation/réattribution, contrôle refusé en fin de lecture, double rafraîchissement, copie du snapshot et callback répété. Les phases rouges (politique absente, puis paramètre resumed absent après la revue) échouent à la compilation avant ajout du comportement attendu, sans assertions exécutées. Les journaux et codes de sortie complets sont conservés sous evidence/call-history-access.

Tests instrumentés/runtime, Galaxy S24+ et conformité graphique D.1 : NON EXÉCUTÉS/NON TESTÉS/NON VALIDÉE. Pas de nouveau résultat Voice, OSINT, tunnel VPN, BLE ou Google Play dans cette itération.

## Procédure S24+ NON TESTÉE

1. Utiliser uniquement un journal et des lignes de test autorisés ; ouvrir Récents sous Sentinel avec rôle et permission, constater les entrées attendues sans exporter de numéros personnels.
2. Dans Paramètres Android > Applications > Applications par défaut > Téléphone, choisir Téléphone Samsung. Revenir à Sentinel : résumé et entrées absents, explication du rôle requis. Tester aussi si Android conserve READ_CALL_LOG.
3. Réattribuer Sentinel : le cache ne doit pas être restauré avant une nouvelle lecture autorisée. Refaire avec retrait puis réattribution de READ_CALL_LOG depuis les paramètres de l'application (Android peut tuer le processus).
4. Pendant une lecture sur un carnet de test volumineux, révoquer puis réattribuer : aucune ancienne génération ne doit remplacer le nouveau résultat. Pour un ordonnancement déterministe, se reporter au test JVM resultReadBeforeRevocationCannotReturnAfterRegrant ; l'ordonnancement physique n'est pas prouvé ici.
5. Vérifier Récents > numéro > pavé sans appel automatique ; rotation et retour premier plan. Retour final à Téléphone Samsung par le menu système précédent. Ne pas désactiver l'application Samsung.

Conserver version du système/app, résultat par scénario et captures expurgées ; ne pas publier un logcat contenant des identifiants personnels.

## Exécution finale et provenance

Sources APK `d5c31b49e84901bb62f1be26072c73d36c2f5bb0`. Commande : `python /workspace/d1-evidence/run-gradle.py call-history-access-final :app:compileDebugKotlin :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest`. Code 0, BUILD SUCCESSFUL en 57 s. 1 397 tests unitaires, 302 suites XML, 0 échec/erreur/ignoré ; sept nouveaux tests de l'état de lecture. Le premier build intermédiaire a également réussi avant le traitement du cas ON_PAUSE relevé en revue ; ne pas le confondre avec le résultat final. Les deux phases rouges et la revue sont conservées.

APK debug et androidTest : apksigner verify --verbose --print-certs et zipalign -c -v 4, codes 0 pour les quatre contrôles. Tailles, empreintes et chemins dans ARTIFACTS.json. Avertissements Compose/Gradle préexistants dans les journaux. Tests instrumentés compilés mais NON EXÉCUTÉS. Les 19 tests Node du candidat d7fce52a sont historiques et NON RÉEXÉCUTÉS ici. Aucun AAB reconstruit.

`adb install -r Sentinel-D1-debug-d5c31b49.apk` installe ce candidat debug ; les anciens noms présents dans les rapports historiques désignent leurs propres APK. Aucun essai de lancement exécuté sur appareil dans cet environnement.
