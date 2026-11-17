# Validation S24+ du correctif bornes et appariement — NO-GO

Les preuves du cycle 9 qualifient ses APK seulement. Ne pas effacer les données ni désinstaller Sentinel habituel.
Le nouveau paquet de qualification doit rester com.sentinel.quantum.aivalidation. La variante copyui locale est distincte et doit être préservée.
Avant installation: vérifier SHA-256, certificat de signature et référence source du paquet livré. Ne pas utiliser install -g ni modifier les rôles Téléphone/SMS/VPN.

1. Collecter le modèle, ABI, SDK, patch Android et les rôles avant/après avec audit/cycle9/validation/collect_device.sh (nouveaux dossiers).
2. Installer l'APK de validation et son APK de tests avec adb install -r et adb install -t -r. Refus de signature: conserver les données et transmettre l'erreur, aucune désinstallation automatique.
3. Rejouer les cinq classes instrumentées du cycle 9 et conserver le journal complet. Les modes par défaut ne prouvent ni action ni reboot.
4. Avec le service local corrigé et le modèle SHA approuvé, effectuer une requête mathématique synthétique. Vérifier réponse JSON/HMAC et refus du rejeu.
5. Mémoriser volontairement l'appariement, effectuer une requête puis annuler/révoquer pendant une nouvelle requête. Aucun ancien résultat ne doit rétablir les droits ou enregistrer un nouveau secret après révocation effective.
6. Déclencher Oublier pendant une requête: après confirmation de l'oubli, une requête sans clé explicite doit échouer; aucun ancien écran ne doit restaurer l'appariement. Un nouvel appariement explicitement consenti reste possible.
7. Révoquer les autorisations du processus depuis un autre écran: aucune sauvegarde/action avec un ancien bail après confirmation de révocation. Le périmètre est le processus Android, pas une révocation serveur du secret.
8. Déconnexion et annulation: utiliser probe_cancel.py avec le PID du service et le vrai binaire ARM64, sans partager la clé. Le processus doit disparaître, le rejeu être refusé, une requête suivante fonctionner.
9. Parcours réel: proposition CREATE_AUDIT_CHECKPOINT -> consentement -> verrouillage Android accepté -> un checkpoint HMAC. Annulation/refus/expiration -> zéro checkpoint; utiliser le protocole baseline/verify de SentinelAiActionJournalEvidenceTest.
10. Refaire les phases prepare/verify et prepare/reboot/verify de SentinelAiPersistenceQualificationTest, une seule préparation par tentative. Un échec conserve la fixture.

Tous ces essais sur le NOUVEL APK sont NON EXÉCUTÉS dans le cloud. Les tests JVM contrôlent l'ordre des courses du coordinateur; ils ne simulent pas une authentification Android réussie.

Limites conservées: appariement oublié localement ne révoque pas le secret du service; barrière d'appariement limitée au processus; restauration conjointe événements+ancre HMAC non détectable; transaction effet/journal non atomique; LiveKit réel et conformité Play non qualifiés. Le service tue/récupère le processus direct llama.cpp; les tests ne certifient pas un exécutable tiers qui créerait ses propres descendants.
