# Runbook de mise en production du service vocal

## Statut

Ce runbook prépare le raccordement du client Android PTT à un service vocal réel. Il
n'active aucun service et ne doit pas être interprété comme une preuve de
production.

Tant que toutes les preuves de sortie ne sont pas réunies :

- le catalogue reste `NOT_FOR_SALE` ;
- la capacité reste `IMPLEMENTED_NOT_CONFIGURED` ;
- aucun secret, endpoint réel ou numéro fournisseur ne doit être commité.

Le contrat fonctionnel et les invariants sont définis dans
[VOICE-SERVICE-CONTRACT.md](./VOICE-SERVICE-CONTRACT.md).

## 1. Préparer les systèmes externes

| Domaine | Précondition de sortie | Preuve à conserver |
|---|---|---|
| Identité | Session utilisateur vérifiée, appareil enregistré, révocation active | Tests d'autorisation positifs et négatifs |
| Entitlement | Droit PTT actif, expiré et révoqué testés | Matrice d'accès horodatée |
| LiveKit | Projet de production, room policy, clés séparées et rotation testée | Configuration exportée sans secret + rapport de rotation |
| Token issuer | Jeton limité à l'identité et à la room, TTL cible ≤ 120 s | Tests TTL, replay, room étrangère et révocation |
| Session store | Création, fermeture et idempotence persistées | Tests de concurrence et de fermeture |
| PSTN éventuel | Fournisseur, pays, coût, présentation et libération de route validés | Appel réel et relevé fournisseur |
| Observabilité | Métriques sans audio ni jeton, alertes et rétention définies | Capture de dashboard et politique de rétention |
| Conformité | Data Safety, confidentialité, sous-traitants et support approuvés | Validations signées |

Les secrets doivent être injectés par le gestionnaire de secrets de l'environnement
d'exécution. Ils ne doivent pas être placés dans GitHub Actions, l'APK, les logs,
les URLs ou les artefacts de test.

## 2. Implémenter l'API serveur

Le serveur doit exposer au minimum :

- `POST /v1/voice/sessions` avec authentification, autorisation,
  `Idempotency-Key`, rate limiting et anti-rejeu ;
- `POST /v1/voice/sessions/{session_id}/close`, idempotent ;
- une révocation immédiate par compte, appareil, entitlement et session.

La réponse de création doit renvoyer uniquement un `server_url` `wss://`, un
jeton éphémère, une expiration et les métadonnées de session nécessaires. Le
serveur doit refuser toute condition inconnue ; le client ne doit jamais fabriquer
de jeton, de room ou de route PSTN.

## 3. Raccorder l'application

Le raccordement Android est acceptable seulement lorsque :

1. l'application obtient les paramètres par l'API authentifiée ;
2. le transport refuse les URL non-`wss://` et les jetons invalides ;
3. la connexion commence microphone coupé ;
4. `press()` publie uniquement depuis l'état READY ;
5. `release()`, `close()`, révocation et erreur coupent le microphone ;
6. aucun jeton ni audio brut n'est persisté ou journalisé.

Le feature flag public reste désactivé pendant toute la phase de certification.

## 4. Validation physique

Utiliser au moins deux appareils Android réels et deux réseaux distincts. Conserver
les identifiants de session anonymisés, jamais les jetons ou le contenu audio.

Scénarios obligatoires :

- PTT bidirectionnel avec transformation active ;
- latence, perte réseau et reconnexion ;
- Bluetooth, haut-parleur, écouteur, mute et interruption ;
- expiration et révocation pendant une session ;
- fermeture normale, crash/reprise et double fermeture ;
- contrôle d'accès compte/appareil/entitlement ;
- appel PSTN réel si cette offre existe.

Chaque scénario doit produire un résultat PASS/FAIL, un horodatage, la version
de l'application, la version du backend et un lien vers les logs techniques
expurgés.

## 5. Passage contrôlé

Le passage public suit cet ordre :

1. déployer le serveur avec une audience interne ;
2. exécuter les tests d'autorisation et de révocation ;
3. réaliser la certification appareils/audio ;
4. faire approuver conformité, facturation, remboursement et support ;
5. activer une audience interne limitée ;
6. observer erreurs, latence, coûts et abus ;
7. ouvrir progressivement le catalogue ;
8. conserver un bouton de rollback qui remet immédiatement
   `NOT_FOR_SALE` et désactive le feature flag.

## Critères de sortie

Le statut ne peut devenir `SENTINEL_VOIP_CERTIFIED` que si les preuves suivantes
sont archivées :

- contrat API versionné et tests automatisés ;
- TTL, replay, révocation et fermeture idempotente validés ;
- room LiveKit de production avec rotation de clés ;
- appels PTT transformés sur appareils réels ;
- PSTN validé si commercialisé ;
- Data Safety, confidentialité et contrats fournisseurs approuvés ;
- entitlement, restauration, expiration, remboursement et support opérationnels ;
- procédure d'incident et rollback exercée.

En l'absence d'un seul de ces éléments, le service reste fermé. Ce runbook ne
change pas ce statut.
