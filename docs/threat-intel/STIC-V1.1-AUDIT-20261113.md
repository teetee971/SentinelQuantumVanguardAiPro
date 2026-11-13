# STIC v1.1 — audit et qualification

Date : 13 novembre 2026 (Guadeloupe)

## Périmètre inspecté

Le dépôt contient un module JavaScript de threat intelligence sous
`security/threat-intelligence/`, mais ne contenait pas le module Python
`threat-intelligence/`, `sentinel_cti`, `jcs.py` ou `crypto.py` mentionné dans
les preuves Termux. Les empreintes candidates Termux n’ont donc pas été
utilisées pour reconstruire du code. Les vecteurs historiques JCS, les 2 011
comparaisons différentielles, les 14 tests crypto et les sauvegardes Termux ne
sont pas des preuves de cette branche.

## Implémentation

Le nouveau package `threat-intelligence/sentinel_cti/` fournit :

- parsing JSON borné avec rejet des clés dupliquées, Unicode invalide, nombres
  non sûrs, constantes non finies et profondeur excessive ;
- canonicalisation JCS déterministe, tri UTF-16 des clés et limites de taille ;
- ECDSA P-256 avec SHA-256, signature IEEE-P1363 de 64 octets, vérification de
  scalaires et rejet de High-S ;
- registre explicite de clés avec environnement, flux, capacités et révocation ;
- validation stricte de l’enveloppe STIC v1.1 et du domaine
  `SENTINEL-STIC-V1.1-ENVELOPE` ;
- vérification UTC de `issued_at`, `valid_from`, `expires_at` ;
- vérification de la taille et du SHA-256 du payload canonique ;
- résultat `VerifiedEnvelope` immuable ;
- aucune mutation du registre et aucune prétention d’anti-rollback durable.

Les octets signés sont explicitement documentés dans `sentinel_cti/envelope.py` :
séparateur de domaine ASCII, en-tête JCS sans `key_id`, payload JCS. `key_id`
est autorisé séparément par le registre et n’est pas ajouté arbitrairement aux
octets signés.

## Tests exécutés

| Contrôle | Résultat | Preuve |
|---|---:|---|
| Tests Python STIC | PASS — 10/10 | `python3 -m unittest discover -s tests -v` |
| Compilation Python | PASS | `python3 -m compileall -q sentinel_cti tests` |
| Régression JavaScript threat-intel | PASS — 56/56 | `npm run test:threat-intel` |
| Contrôle espaces/diff | PASS | `git diff --check` |
| Vecteurs historiques Termux | NON TESTÉ | fichiers absents du checkout |
| Intégration Android | NON TESTÉ | aucune liaison Python→Android existante |
| Anti-rollback persistant | NON IMPLÉMENTÉ | aucun stockage durable fourni |

## Anomalies corrigées dans ce périmètre

- absence complète de l’enveloppe STIC vérifiable dans le dépôt : package ajouté ;
- risque de clés JSON dupliquées : rejet avant canonicalisation ;
- risque de signature ECDSA mal formée ou High-S : rejet strict ;
- confusion de clé/environnement/flux/capacité : registre d’autorisation ;
- substitution de payload ou incohérence digest/taille : liaison canonique vérifiée ;
- résultat mutable après vérification : structures exposées immuables.

## Risques résiduels

- le contrat historique Termux n’est pas disponible ; une compatibilité exacte
  avec ses octets signés n’est pas démontrée ;
- aucune persistance anti-rejeu ou anti-rollback n’est fournie ; le vérificateur
  ne doit pas être présenté comme une autorité de séquence ;
- la dépendance `cryptography` doit être installée et verrouillée par le
  consommateur ; aucun lockfile Python n’existe dans le dépôt ;
- aucune intégration applicative de cette enveloppe n’est encore raccordée au
  pipeline JavaScript ou Android ;
- les tests n’ont pas été exécutés sur ARM64 Termux ni sur Galaxy S24+.

Le critère de fin de mission n’est donc pas atteint. La PR doit rester en
brouillon jusqu’à récupération des vecteurs historiques et validation de
l’intégration réelle.

## Reproduction

```bash
cd threat-intelligence
python3 -m pip install -r requirements.txt
python3 -m unittest discover -s tests -v
python3 -m compileall -q sentinel_cti tests
cd ..
npm run test:threat-intel
```
