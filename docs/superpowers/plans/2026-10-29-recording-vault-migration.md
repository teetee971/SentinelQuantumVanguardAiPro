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
- [ ] Écrire les tests source intacte, nouveau IV, idempotence et downgrade.
- [ ] Exécuter la version initiale pour constater la migration manquante.
- [ ] Rendre le lanceur hôte capable de construire le vrai JNI Linux.

### Task 2: implémentation et reprise

Files: RecordingVaultStore.kt, RecordingVaultCrypto.kt, nouveau VaultIo.kt.
- [ ] Ajouter domaine marqueur et empreintes, opérations fichier injectables.
- [ ] Implémenter L/P/R/C, reprise et suppression dans l'ordre de la spec.
- [ ] Exécuter tests fonctionnels, erreurs IO et mort de processus.

### Task 3: protocole et preuves

Files: tests androidTest migration, qualification/run-device.sh, test-device-runner.py, documentation.
- [ ] Tester puis remplacer le comptage fixe par découverte et comparaison des identités.
- [ ] Ajouter migration réelle au protocole d'interruption et commandes Termux.
- [ ] Exécuter tests hôte, git diff --check et tentative build Android isolé.
- [ ] Revue indépendante, corrections, journal des résultats ; commits et PR brouillon.
