# Sentinel Voice Session Service

Service séparé pour les sessions PTT LiveKit de Sentinel. Il ne remplace pas
`backend/wangiri-api` et ne doit pas être fusionné avec cette API.

## État par défaut

Le service est fermé par défaut :

```text
VOICE_SERVICE_ENABLED=false
```

Dans cet état, `/health/live` répond pour permettre le déploiement contrôlé,
mais la readiness et les endpoints de session répondent `503`. Aucun jeton
LiveKit ne peut être créé.

## Configuration de production

Les variables suivantes doivent être injectées par Render, jamais commitées,
placées dans l’APK ou écrites dans les logs :

- `VOICE_SERVICE_ENABLED=true` uniquement après certification ;
- `VOICE_REDIS_URL` ;
- `LIVEKIT_URL` en `wss://` ;
- `LIVEKIT_API_KEY` et `LIVEKIT_API_SECRET` ;
- `VOICE_AUTH_JWKS_URL`, `VOICE_AUTH_ISSUER` et `VOICE_AUTH_AUDIENCE` ;
- `VOICE_ENTITLEMENT_CLAIM` si le fournisseur d’identité utilise un nom différent.

Le JWT OIDC doit contenir `sub`, `exp`, `iat`, un `device_id` ou `device_ids`,
et un claim d’entitlement strictement égal à `true`. Le service vérifie aussi
les révocations Redis, le rate limit et la correspondance appareil/session.

Les tokens LiveKit sont limités à l’identité et à la room dérivée côté serveur,
avec une durée maximale de 120 secondes. Les destinations PSTN sont refusées
dans cette première version. Aucun audio n’est persisté.

## Validation avant activation

L’activation exige les preuves du contrat dans
`docs/VOICE-SERVICE-CONTRACT.md` et du runbook dans
`docs/VOICE-PRODUCTION-READINESS-RUNBOOK.md`, notamment identité réelle,
révocation, certification de deux appareils Android et conformité Data Safety.
