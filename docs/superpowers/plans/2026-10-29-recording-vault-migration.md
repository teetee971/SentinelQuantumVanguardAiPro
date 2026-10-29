# Recording Vault migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** migrer V1 sans perte et sans repli V1 après engagement V2.
**Architecture:** candidat immuable, marqueur authentifié puis publication JNI NOREPLACE. Reprise sous le verrou existant et synchronisation obligatoire.
**Tech Stack:** Kotlin, AES-GCM AndroidKeyStore, java.nio, JNI Linux/Android.
**Spec:** docs/superpowers/specs/2026-10-29-recording-vault-migration-design.md

## Global Constraints

Branche fix/recording-vault-v2-migration-hardening-20261028 ; main inchangée.
Conserver V1 et le JNI RENAME_NOREPLACE ; aucune suppression des données Android.
Aucun résultat Android inventé ; Dependabot NON VÉRIFIÉ HTTP403.

## Review Focus

- Coupure entre rename et fsync : résultat indéterminé, reprise obligatoire.
- Marqueur sans candidat : refus, conservation V1.
- Suppression interrompue : aucun repli V1.
- V2 préexistant sans marqueur : enrôlement authentifié.
- Liens et collisions de noms : rejet sans écrasement.

### Task 1: conception et tests de migration

Files: spec, qualification/host/HostTests.kt, qualification/run-host-tests.sh.
Interface: RecordingVaultStore.migrate(id: String): Boolean ; VaultIo injectable.
- [x] Écrire les tests source intacte, nouveau IV, idempotence et downgrade.
- [x] Exécuter la version initiale pour constater la migration manquante.
- [x] Rendre le lanceur hôte capable de construire le vrai JNI Linux.

### Task 2: implémentation et reprise

Files: RecordingVaultStore.kt, RecordingVaultCrypto.kt, nouveau VaultIo.kt.
- [x] Ajouter domaine marqueur et empreintes, opérations fichier injectables.
- [x] Implémenter L/P/R/C, reprise et suppression dans l'ordre de la spec.
- [x] Exécuter tests fonctionnels, erreurs IO et mort de processus.

### Task 3: protocole et preuves

Files: tests androidTest migration, qualification/run-device.sh, test-device-runner.py, documentation.
- [x] Tester puis remplacer le comptage fixe par découverte et comparaison des identités.
- [x] Ajouter migration réelle au protocole d'interruption et commandes Termux.
- [x] Exécuter tests hôte, git diff --check et tentative build Android isolé.
- [x] Revue indépendante, corrections, journal des résultats ; commits et PR brouillon.

## Résultats et décisions

Implémentation et qualification hôte réalisées ; build Android et lint désormais
réussis sur Linux x86_64, y compris offline après mise en cache.
Qualification Galaxy via Termux exécutée par l’utilisateur : transcription du
run c48883da20ff488ba71a09f7b2a57c95 reçue (38 tests et phases complètes).
Redémarrage complet et coupure électrique NON EXÉCUTÉS, voir le rapport et les preuves dans
qualification/MIGRATION-QUALIFICATION.md et qualification/evidence-migration/.
Les cases ci-dessus indiquent des actions effectuées, pas des PASS Android.

Décision de revue : récupérer un save pré-marqueur uniquement par authentification
du candidat lorsqu’aucune source ni autorité n’existe ; préserver tout candidat
invalide. Coût : un candidat incomplet reste bloqué jusqu’à une décision explicite.
Décision de revue : forcer un V2 canonique avant enrôlement, même si les anciens
producteurs forçaient déjà leurs écritures. Coût : E/S supplémentaires aux lectures
d’enrôlement. Aucune restriction de durabilité relâchée pour Android.
