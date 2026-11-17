# Cycle 9 — livraison pour validation physique — NO-GO

Source: 8ff3ceb38745846d07b7a593aba2fde3dad32822.
APK Android construit depuis bf233ec93e08015fec5e86e35fb52c1f033e44f5; arbre native-android-app identique au dernier commit.
Paquet distinct com.sentinel.quantum.aivalidation; ne remplace pas com.sentinel.quantum.
Aucun moteur Qwen ni poids embarqué: runtime local Termux/proot nécessaire. Aucun service payant.

## Preuves conservées, non relancées pendant cette livraison
JVM: 344 suites, 1633 tests, zéro échec/erreur/ignoré; Gradle --rerun exit 0.
Python local: 13; vocal: 38; Wangiri avec Redis de test isolé: 73; zéro échec/erreur/ignoré dans les rapports finaux.
Les premières tentatives Wangiri ont échoué pour import/Redis absent; leurs journaux sont inclus.
APK et tests instrumentés compilés; signature debug, certificat SHA-256 c45c6e78bc19ffa9b8fc41a6b10520fc3eefe788857bb466dd143c852096c6f6.
Aucun test instrumenté exécuté ici, aucun appareil Android connecté à cet environnement.
Annulation Qwen observée sur x86_64 uniquement: aucune preuve ARM64 Android.

## P0 / P1 restant
P0: exécution Qwen ARM64 et parcours Android complet non démontrés; authentification/KeyStore/révocation/HMAC à exécuter sur S24+.
P1: LiveKit réel non qualifié, faux adaptateur de test ne démontre pas révocation des JWT existants.
P1: restauration simultanée des deux fichiers HMAC non détectable; effet et journal non transactionnels.
P1 Play: signalement des contenus IA et modération non qualifiés; déclarations Play Console/Data Safety non examinées.
Aucun GO production.

## Récupération des sources sans toucher à la copie de référence
Depuis le dépôt propre au commit caa43c1392b970335125b2b2aa3f8ea4ef6b4a47, vérifier le SHA256 du bundle puis:

    git bundle verify CHEMIN/sentinel-cycle9.bundle
    git fetch CHEMIN/sentinel-cycle9.bundle HEAD
    git worktree add -b validation/s24-cycle9 ../sentinel-s24-cycle9 FETCH_HEAD

Le worktree séparé préserve validation/s24-cycle8. Aucun reset/clean/push/main.

## APK et première collecte physique
Vérifier SHA256SUMS avec sha256sum -c. Depuis Termux, chemins à adapter aux fichiers téléchargés:

    adb install -r CHEMIN/sentinel-ai-cycle9-arm64-validation.apk
    adb install -t -r CHEMIN/sentinel-ai-cycle9-instrumentation.apk

Ne pas utiliser -g; ne pas attribuer les rôles Téléphone/SMS/CallScreening au paquet de validation.
Depuis le nouveau worktree:

    bash audit/cycle9/validation/collect_device.sh "$HOME/sentinel-c9-before-$(date +%Y%m%d-%H%M%S)"
    adb shell am instrument -w -e class com.sentinel.quantum.ai.SentinelAiNetworkPolicyInstrumentationTest,com.sentinel.quantum.ai.SentinelAiActivityInstrumentationTest,com.sentinel.quantum.ai.SentinelAiAuditJournalInstrumentationTest,com.sentinel.quantum.ai.SentinelAiPersistenceQualificationTest,com.sentinel.quantum.ai.SentinelAiActionJournalEvidenceTest com.sentinel.quantum.aivalidation.test/androidx.test.runner.AndroidJUnitRunner

Conserver la sortie intégrale et le statut INSTRUMENTATION_CODE, pas uniquement le code de sortie adb.
Le test persistence par défaut est roundtrip, PAS preuve de redémarrage. Le test action par défaut est baseline, PAS preuve d'action humaine.
Avant de lancer les phases prepare/verify/reboot et les actions manuelles, consulter les arguments exacts dans leurs sources transférées. Ne pas effacer les données ni modifier le téléphone de référence.

## Matrice physique obligatoire (tous NON EXÉCUTÉS ici)
Qwen natif ARM64: ELF AArch64 + modèle SHA + réponse réelle; mémoire/température/latence à mesurer.
Annulation: probe_cancel.py et monitor_runtime.py; enfant arrêté après déconnexion, requête suivante utilisable, rejeu refusé.
Android: refus sans consentement, mauvais secret, validation explicite + code système, annulation code, expiration, changement écran et révocation globale.
HMAC: concurrence, modification d'un fichier propre au test refusée, changement de processus, reboot après déverrouillage.
Parcours: demande réelle -> proposition -> code système -> checkpoint -> journal vérifié, fenêtre de checkpoints 0 pour refus/annulation, 1 pour acceptation.
PhoneCore/SMS/MMS/VPN/Wi-Fi/Bluetooth: collecte avant/après; aucune permission ou configuration modifiée automatiquement.
