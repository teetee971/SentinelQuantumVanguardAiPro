# Sentinel Wangiri API

Statut : **service Render déployé et vérifié le 15 septembre 2026** — tâche `b556bff2-2194-4b64-a0e3-722fd12c0673`.

URL officielle : https://sentinel-moteur-api.onrender.com/

Cette API FastAPI enrichit le filtrage local avec un score explicable de fraude Wangiri et de spoofing. Elle ne remplace pas le chemin critique Android : `CallScreeningService` doit toujours répondre localement dans le délai Android, sans attendre Render ou Redis.

## Garanties

- connexion Upstash en `rediss://` avec validation TLS active ;
- aucun secret dans Git ;
- aucun numéro brut persisté : les clés Redis utilisent HMAC-SHA-256 avec `PHONE_HASH_PEPPER` ;
- score multi-signal : une nationalité ou un indicatif ne suffit jamais à bloquer ;
- fonctionnement dégradé si Redis expire : le score local reste rendu, sans réputation ;
- sonde anti-rejeu bornée : vérification réelle de `SET NX PX`, clé aléatoire à TTL court, suppression immédiate et cache de 5 minutes ;
- signalements protégés par `REPORT_API_KEY` et écriture Redis atomique ; nonce dédupliqué 24 h, même rapporteur/numéro/catégorie limité à une contribution sur 7 jours, réputation expirée après 180 jours ;
- schémas Pydantic stricts, taille des entrées bornée, CORS par allowlist ;
- quotas séparés par endpoint et par client pseudonymisé, complétés par une limite globale ;
- réponses HTTP 429 avec `Retry-After`, sans stockage d’adresse IP brute.

## Endpoints

- `GET /health/live` : processus vivant ;
- `GET /health/ready` : Redis joignable ;
- `POST /v1/evaluate-call` : évaluation ;
- `POST /v1/report-call-public` : signalement utilisateur à faible niveau de confiance, stocké uniquement dans la file pending ;
- `GET /v1/moderation/pending` : liste administrative pseudonymisée des rapports pending, authentifiée par `X-Moderation-Key` ;
- `POST /v1/moderation/decision` : approbation/rejet administratif atomique ; seule une approbation peut promouvoir un signal vers la réputation live ;
- `POST /v1/report-call` : signalement serveur-à-serveur authentifié, atomique et dédupliqué.

Exemple :

```bash
curl -sS https://sentinel-moteur-api.onrender.com/v1/evaluate-call \
  -H 'Content-Type: application/json' \
  -d '{
    "caller_number": "+33123456789",
    "recipient_country": "FR",
    "ring_duration_ms": 900,
    "verification_status": "NOT_VERIFIED"
  }'
```

`ring_duration_ms` est surtout un signal post-appel : au début d'un appel entrant, sa durée finale est inconnue. Le client Android ne doit jamais inventer cette valeur. `verification_status` est un signal réseau, pas une preuve d'identité.

## Déploiement Render gratuit avec GitHub

1. Dans Upstash, révoquer toute clé ayant été publiée et copier une nouvelle URL TLS `rediss://`.
2. Fusionner la PR uniquement après les contrôles CI.
3. Se connecter à [Render](https://dashboard.render.com/) avec GitHub.
4. Autoriser Render uniquement sur le dépôt `SentinelQuantumVanguardAiPro`.
5. Dans Render, choisir **New > Blueprint** puis sélectionner le dépôt.
6. Render détecte le `render.yaml` à la racine et prépare `sentinel-moteur-api`.
7. Lorsque Render le demande, saisir `REDIS_URL` comme secret. Ne jamais mettre la valeur dans le YAML.
8. Laisser Render générer `PHONE_HASH_PEPPER`, `RATE_LIMIT_PEPPER` et `REPORT_API_KEY`. Conserver la valeur de `REPORT_API_KEY` uniquement côté service autorisé ; ne pas l'embarquer dans un APK public.
9. Valider le Blueprint. Le conteneur écoute `0.0.0.0:$PORT` et Render vérifie `/health/ready`.
10. Dans les journaux Render, vérifier le démarrage puis ouvrir :
    - `https://sentinel-moteur-api.onrender.com/health/live`
    - `https://sentinel-moteur-api.onrender.com/health/ready`
    - `https://sentinel-moteur-api.onrender.com/docs`
11. Faire l'appel `curl` ci-dessus et confirmer que `community_intelligence` vaut `available`.

## Preuve runtime du 15 septembre 2026

Déploiement Render Free du commit `61d5dee` :

- `GET /health/live` → HTTP 200, `{"status":"ok"}` ;
- `GET /health/ready` → HTTP 200, `{"status":"ready","redis":"connected"}` ;
- `GET /openapi.json` → HTTP 200 ;
- `POST /v1/evaluate-call` avec `+33123456789` → HTTP 200 et `community_intelligence: available`.

Cette preuve établit le fonctionnement ponctuel du runtime et de Redis. Après le déploiement du commit `dc067563`, un nouveau contrôle du 15 septembre 2026 a obtenu `GET /health/ready` → HTTP 200 avec `{"status":"ready","redis":"connected","replay_guard":"available"}` : la sémantique atomique `SET NX PX` a donc été démontrée sur le Redis réellement raccordé. Elle ne constitue ni SLA, ni validation de charge, ni disponibilité permanente. Le plan gratuit peut subir un démarrage à froid.
12. Garder **Auto-Deploy: After CI Checks Pass**. Le Blueprint utilise `autoDeployTrigger: checksPass`.

Le plan gratuit peut se mettre en veille et provoquer un démarrage à froid. L'application Android doit donc conserver son moteur local et traiter l'API comme un enrichissement facultatif.

## Développement local

```bash
cd backend/wangiri-api
python -m venv .venv
. .venv/bin/activate
pip install -r requirements-dev.txt
pytest -q
uvicorn app_redis:app --reload
```

Ne chargez pas un fichier `.env` dans le code de production. Injectez les variables via l'environnement du processus.

## Preuve anti-rejeu Redis

La disponibilité Redis ne suffit pas : un simple `PING` ne prouve pas l'exclusion atomique des rejeux. Le contrôle `/health/ready` exécute donc une sonde isolée :

1. création d'une clé aléatoire avec `SET key 1 NX PX 15000` ;
2. seconde écriture `NX` qui doit être refusée ;
3. suppression immédiate de la clé ;
4. mise en cache du succès pendant 5 minutes (30 secondes après échec).

La readiness échoue en HTTP 503 si la sémantique attendue n'est pas démontrée. Cette sonde ne contient aucun numéro, rapport utilisateur ou secret. La preuve de déploiement ci-dessus reste celle du commit indiqué ; la nouvelle propriété `replay_guard` ne doit être considérée comme opérationnelle qu'après vérification sur le runtime Render mis à jour.


## Limites de la réputation communautaire

Un signalement accepté n'est jamais une preuve de fraude et ne doit pas être affiché comme une identité. L'écriture Redis est atomique : le nonce, la fenêtre pseudonymisée du rapporteur, les compteurs de catégorie et les dates de première/dernière observation sont mis à jour ensemble. Une même source pseudonymisée ne contribue qu'une fois par numéro et catégorie sur sept jours.

Cette limitation réduit le bourrage simple ; elle ne remplace pas la modération humaine, le recours, la détection de brigading ou une identité d'appareil attestée. Les réseaux partagés peuvent sous-compter des rapports légitimes. L'API de signalement reste serveur-à-serveur : la clé ne doit jamais être embarquée dans l'APK ou le navigateur.

## Public community reports

`POST /v1/report-call-public` accepts user-submitted call reports **without embedding `REPORT_API_KEY` in the Android app**. These reports are intentionally low-trust and are written only to a pending moderation namespace in Redis.

They do not increment `phone:spam:v2:*` and therefore do not change the live reputation score while pending. The endpoint is rate-limited, nonce-deduplicated and reporter-deduplicated.

Moderation is explicit and separate from public reporting. `GET /v1/moderation/pending` exposes only the HMAC phone fingerprint and bounded aggregate counts to an authenticated administrator. `POST /v1/moderation/decision` requires an independent `MODERATION_API_KEY`: `APPROVE` atomically consumes one matching pending signal and increments the trusted reputation once; `REJECT` consumes the pending signal without changing live reputation. The moderation key is server-side only and must never be embedded in Android or public JavaScript. Prefer a dedicated server-side `PUBLIC_REPORT_PEPPER`; when it is absent, the service derives a domain-separated sub-secret from the existing server-side `PHONE_HASH_PEPPER`. No report secret is sent to Android. Raw phone numbers are parsed transiently and are not persisted by this module.

The trusted `POST /v1/report-call` endpoint remains server-to-server and still requires `X-Report-Key`.
