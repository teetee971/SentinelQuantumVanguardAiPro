# Vérifier l'APK de tests installé depuis Termux

Le terminal fourni par l'utilisateur confirme la connexion à192.168.11.173:38703 et le package principal versionCode22/versionName1.0.9. Cette preuve est fournie par l'utilisateur ; l'hôte Codex reçoit toujours Connection refused. Aucun test Android n'a été exécuté par ce diagnostic.

Télécharger diagnostic-sentinel-readonly.sh puis, dans Termux :

```bash
bash "$HOME/storage/downloads/diagnostic-sentinel-readonly.sh" 192.168.11.173:38703
```

Aucun git pull/checkout n'est nécessaire dans le worktree du téléphone. Le script ne touche pas à ses modifications locales. L'adresse peut être remplacée par celle affichée actuellement dans les réglages.

## Actions exactes

Connexion/get-state ADB, dumpsys des versions du package principal, liste des instrumentations Sentinel, pm path du package com.sentinel.quantum.test, puis copie **de cet APK de code seulement** dans un nouveau répertoire privé Termux $HOME/sentinel-diagnostic-XXXXXX. Aucun accès aux conversations/préférences/bases/keystores, aucune installation, instrumentation, demande de rôle ou suppression de données.

Le Python embarqué calcule une empreinte des entrées ZIP et de leurs octets, en excluant uniquement répertoires et métadonnées de signature JAR. Le bloc de signature APKv2 ne fait pas partie des entrées ZIP. Une nouvelle signature locale peut donc changer le SHA256 global sans changer l'empreinte du code. Aucun APK n'est extrait ou exécuté. Budget :16MiB de fichier,4096 entrées,64MiB décompressés,32MiB par entrée ; noms dupliqués/dangereux et liens symboliques refusés. Les APK split ou emplacements non pris en charge ne sont pas déclarés cohérents.

Le script utilise python3/python Termux si disponible, sinon le proot Ubuntu déjà présent avec un bind du seul dossier de diagnostic. Il n'installe aucun paquet. Un échec de requête ADB n'est pas confondu avec un paquet absent.

## Références effectivement construites

- Code isolé2add1102 : APK SHA2569defb9f68a40b2ce2554159e844ed32b3aab42a1aef0c1a84a9094b01924f748 ; empreinte contenu7240ba552a7c96aa34a167079adc48ce25742187ab11efd704df02ad6219e21a ;56 entrées.
- Ancien AndroidTest v22 : APK SHA25684e3b6462b15732253818822106d7789262ab4b5aca087ea17ee3ce7437eba3e ; empreinte contenu5368c3e3b8ba67e31ad56804dea532132f4b437cd46ebdeece552c8d9c2f9420 ;56 entrées. Ce contenu possède les anciens tests qui écrivent en production.

Codes de sortie :0 MATCH_ISOLATED_2add1102 ;4 LEGACY_UNGUARDED_DO_NOT_RUN ou DIFFERENT_DO_NOT_RUN ;2 connexion/requête échouée ;3 paquet absent, disposition non prise en charge ou Python indisponible ;5 archive illisible/hors limites.

**MATCH identifie le contenu uniquement.** Ce n'est ni une validation de signature/compatibilité, ni un PASS de tests, ni une autorisation d'exécuter une suite sur le S24+. Le JSON indique toujours signature=NOT_VERIFIED et tests_executed=false. Le script ne lance aucun test même en cas de correspondance.

Partager le texte RESULTATS.txt affiché à la fin suffit ; inutile de transmettre l'APK copié. Aucun envoi externe automatique. Le contenu réellement installé reste inconnu tant que ce résultat n'a pas été fourni.

## Validation hôte

bash -n et neuf tests Python réussis, sans test ignoré : modification DEX détectée ; nouvelles métadonnées de signature sans changement de contenu ; chemins/duplicatas refusés ; budget décompressé ; APK isolé réel reconnu ; exécution complète du script avec ADB simulé pour APK courant, ancien, paquet absent et requête interrompue. Chaque commande ADB simulée est vérifiée contre une liste de lecture seule. Ces simulations ne constituent pas une exécution sur le téléphone.

Commande exacte : python3 qualification/test-guard/test_readonly_diagnostic.py. Les fixtures APK réelles peuvent être désignées via SENTINEL_CURRENT_TEST_APK et SENTINEL_LEGACY_TEST_APK ; leur absence fait explicitement SKIP les cas qui en ont besoin. Toutes étaient présentes lors de l'exécution rapportée ici.

SHA256 du script livré :26e90d40e70928b59f6f6ed111764678bc3d067612bc3116a1c11cacd9934a7c.
