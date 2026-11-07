# MMS v22 — contrôle de la limite opérateur

Statut global : **BLOCKED**. Galaxy S24+ : **NOT_TESTED** pour v22.

Source qualifiée : `fafe9f5b1c57b21f276ca62189acab625a490ec9`. Le SHA du commit final de preuves est vérifié séparément, avec comparaison de l’arbre Android.

## Défaut établi et portée du correctif

Le chemin d’envoi v21 acceptait jusqu’à 10 Mio de pièces jointes sans comparer le PDU complet à la limite opérateur. Android peut rejeter un PDU trop gros avec MMS_ERROR_IO_ERROR. Les limites 307200 et 614400 octets apparaissent sur l’appareil, mais la sortie filtrée ne prouve pas la configuration effective ni le dépassement du MMS échoué. Sa taille reste inconnue. Le code 5 peut aussi provenir d’autres erreurs IO ; aucun succès opérateur n’est revendiqué.

La v22 lit la configuration via le SmsManager de l’abonnement explicitement sélectionné, puis utilise la même instance pour le transport. Le composeur compare tous les octets du PDU, en-têtes compris, à maxMessageSize. Une limite inconnue, nulle ou négative bloque l’envoi. Un dépassement est refusé avant toute création de fichier, projection provider ou soumission. L’interface existante affiche un motif explicite et conserve le brouillon. Aucune compression automatique, permission générale, relance réseau, nouvel écran ou dashboard. Les limites locales restent actives.

## Preuves de régression

Onze tests ciblés, dont six nouveaux, reproduisent cinq échecs avant correction ; les onze passent après correction. Cas : taille supérieure, en-têtes seuls faisant franchir la limite, deux limites de SIM différentes, limite indisponible, frontière exacte et un octet de moins, maintien de la borne locale de sécurité. Le garde de version échoue sur 21 avant passage à 22, puis ses sept contrôles passent.

## Qualification locale

Contrat visuel : 1 test, aucun échec. Suite complète : 1427 tests, aucun échec ; un ignoré sans fixture, puis aucun ignoré avec le nouvel APK. Lint : 0 erreur, 221 warnings, 19 hints ; mêmes IDs et décomptes que la classification précédente, sans suppression. assembleDebug et assembleDebugAndroidTest : PASS. Exécution instrumentée sur appareil : NOT_TESTED. Signature v2 debug et alignement : PASS. Contrats produit : 69 PASS ; hygiène credentials : 6 PASS ; audit npm : 0 vulnérabilité. Scan APK de motifs de secrets : 0 détection, non exhaustif. Détails Dependabot inaccessibles, aucune clôture affirmée.

APK : com.sentinel.quantum, versionCode22, versionName1.0.9, minSdk24, targetSdk36, compileSdk37. Chemin et SHA-256 exacts dans SUMMARY.json. Schémas Room, dépendances et wrapper inchangés. Les risques lint résiduels incluent compatibilité API/OEM, identifiants pseudonymes et sauvegarde, visibilité des applications, écritures synchrones et accessibilité réelle.

## Appareil et suite

L’utilisateur a confirmé v21 installée, lancement réussi, miniatures de MMS existants envoyés/reçus désormais affichées. Un nouvel envoi reste en échec code5, sans HTTP fourni. Les données personnelles des captures ne sont pas archivées. Ces observations v21 ne qualifient pas v22.

Le kit privé référencé dans SUMMARY.json signe localement avec la clé existante, sans la transmettre. Autotest avec la seule clé cloud de fixture ; signature locale utilisateur confirmée ; installation v22 encore en attente. Voir [l’erratum du rapport](SIGNING_REPORT_ERRATUM_20261107.md). Ne pas désinstaller, vider les données ni restaurer le fichier MMS modifié dans le dépôt Termux. Restent notamment : lecture réelle de la configuration SIM, refus d’un MMS trop gros, envoi/réception sous la limite, appels/filtrage, SMS multiparties/Unicode, notifications arrière-plan, révocation des permissions, seconde SIM si disponible et conservation exhaustive des données Room/provider.

Commandes, journaux, XML JUnit, rapports lint et empreintes des sources : RAW_EVIDENCE.zip. Publication privée via le plugin Google Drive ; aucune nouvelle permission publique. Aucun workflow Actions déclenché manuellement, aucune fusion.
