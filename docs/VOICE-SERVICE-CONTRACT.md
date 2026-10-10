# Contrat du service d’appel Sentinel

## But

Ce document définit le minimum nécessaire pour raccorder le client Android
LiveKit/PTT à un service Sentinel réel. Il ne constitue pas une activation du
service : tant que les prérequis d’infrastructure, de conformité et de
validation physique ne sont pas prouvés, le catalogue reste `NOT_FOR_SALE`.

Le client Android ne doit jamais fabriquer un jeton LiveKit, choisir librement
une room, ni appeler directement un fournisseur PSTN.

## Autorité et préconditions

Le service doit disposer d’une identité utilisateur authentifiée et d’un
contexte d’appareil vérifié. Avant d’émettre une session, il doit vérifier :

1. l’identité et l’état du compte ;
2. l’entitlement voix/PTT actif et non expiré ;
3. la révocation du compte, de l’appareil et de la session ;
4. le consentement et les notices de traitement audio applicables ;
5. les limites de débit, l’anti-abus et le budget d’appel ;
6. la disponibilité de la room LiveKit et, pour un numéro classique, de la
   passerelle VoIP/PSTN.

Toute vérification absente ou indéterminée doit refuser la session. Un
entitlement client ne remplace jamais l’autorisation d’utiliser un numéro ou un
réseau tiers.

## API minimale

### Création d’une session

`POST /v1/voice/sessions`

En-têtes :

- `Authorization: Bearer <session utilisateur>`
- `Content-Type: application/json`
- `Idempotency-Key: <valeur aléatoire par tentative>`

Corps minimal :

```json
{
  "channel": "PTT_ROOM",
  "destination": {
    "type": "sentinel_room",
    "room_id": "contact-or-group-reference"
  },
  "client": {
    "device_id": "device-reference",
    "app_version": "version",
    "platform": "android"
  }
}
```

Pour un appel vers un numéro, `destination.type` devient
`pstn_number` et le numéro doit être normalisé côté serveur. Le serveur doit
appliquer les règles d’autorisation, de pays, de coût et de présentation du
numéro avant toute réservation de route.

Réponse `201 Created` :

```json
{
  "session_id": "voice-session-id",
  "transport": {
    "server_url": "wss://voice.example.invalid",
    "access_token": "ephemeral-token",
    "expires_at": "2026-10-10T12:00:00Z"
  },
  "media": {
    "voice_transform_required": true,
    "ptt_required": true
  }
}
```

Le jeton est éphémère, limité à cette room et à cette identité, et doit
autoriser uniquement les grants nécessaires. Sa durée doit rester courte
(cible : 120 secondes maximum, avec renouvellement explicite et audité si un
appel le justifie). Le serveur ne doit jamais renvoyer de secret permanent.

Le client transmet uniquement `server_url` et `access_token` au transport
Android. Il ne les persiste pas, ne les journalise pas et refuse toute URL qui
ne respecte pas la politique `wss://` déjà appliquée par
`SentinelLiveKitCallTransport`.

### Fermeture et révocation

`POST /v1/voice/sessions/{session_id}/close`

La fermeture doit être idempotente. Elle doit retirer la session côté serveur,
révoquer ou laisser expirer le jeton, libérer la route PSTN et arrêter la
facturation éventuelle.

Une révocation de compte, d’appareil, d’entitlement ou de session doit couper
la publication média côté serveur et empêcher toute reconnexion avec le même
contexte.

## Invariants PTT

Le serveur doit accepter le média uniquement dans une room authentifiée et
autorisée. Le client applique ensuite le contrat PTT suivant :

- connexion avec microphone désactivé ;
- `press()` : publication autorisée uniquement si la session est READY ;
- `release()` : coupure immédiate ;
- `close()` ou erreur de transport : coupure avant fermeture ;
- échec de permission, de room, de jeton ou de publication : état FAILED et
  aucun média brut de secours.

Le serveur ne doit pas interpréter l’existence d’une connexion comme une
autorisation de transmettre en continu. Les événements de session, de
publication et de révocation doivent être corrélables par `session_id`, sans
enregistrer le contenu audio.

## Sécurité et confidentialité

- Ne jamais placer un jeton dans une URL, un log, une métrique ou une
  exception.
- Ne jamais accepter une room ou un fournisseur PSTN fourni sans validation
  serveur.
- Ne jamais persister l’audio par défaut.
- Documenter région d’hébergement, sous-traitants, journaux techniques,
  métadonnées d’appel, durées de conservation et suppression.
- Protéger les endpoints par authentification, autorisation, rate limiting,
  anti-rejeu et idempotence.
- Prévoir rotation/révocation des clés LiveKit, des credentials PSTN et des
  secrets de signature.
- Émettre des erreurs génériques côté client ; conserver les détails
  diagnostiques uniquement dans des journaux protégés et bornés.

## Preuves avant activation

La capacité ne peut passer à `SENTINEL_VOIP_CERTIFIED` et le checkout ne peut
pas être ouvert avant d’avoir produit :

- tests d’autorisation positifs et négatifs ;
- preuve de TTL, révocation et impossibilité de réutiliser un jeton ;
- room LiveKit de production avec observabilité et rotation de clés ;
- appel PTT bidirectionnel transformé sur appareils réels ;
- appel VoIP/PSTN réel si les numéros classiques sont proposés ;
- mesures latence, écho, Bluetooth, haut-parleur, écouteur, mute, hold,
  interruption et reconnexion ;
- fiche Data Safety, politique de confidentialité et contrats fournisseurs
  validés ;
- entitlement, restauration, expiration, remboursement et support validés.

En l’absence de ces preuves, l’état public correct reste
`IMPLEMENTED_NOT_CONFIGURED` et le service doit rester fermé.
