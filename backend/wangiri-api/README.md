# Sentinel Wangiri API

Statut : **service Render déployé et vérifié le 15 septembre 2026** — tâche `b556bff2-2194-4b64-a0e3-722fd12c0673`.

URL officielle : https://sentinel-moteur-api.onrender.com/

Cette API FastAPI enrichit le filtrage local avec un score explicable de fraude Wangiri et de spoofing. Elle ne remplace pas le chemin critique Android : `CallScreeningService` doit toujours répondre localement dans le délai Android, sans attendre Render ou Redis.

## Garanties

- connexion Upstash en `rediss://` avec validation TLS active ;
- aucun secret dans Git ;
- aucun numéro brut persisté : les clés Redis utilisent HMAC-SHA-256 avec `PHONE_HASH_PEPPER` ;
- score multi-signal : une nationalité ou un indicatif ne suffit jamais à bloquer ;
- `/v1/evaluate-call` est strictement en lecture sur la réputation : consulter un numéro ne crée ni signalement, ni « vague d’appels », ni récence artificielle ;
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

Quand une réputation communautaire modérée existe, la réponse expose aussi `reputation_observed_at_ms` et `reputation_ttl_ms`. Ces champs décrivent la fraîcheur de cette réputation ; ils restent `null` lorsqu'aucune preuve de récence n'existe. Le simple fait d'évaluer un numéro ne réécrit jamais ces valeurs.

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

Accepted call-report categories are `WANGIRI`, `SPOOFING`, `PREMIUM_RATE`, `ROBOCALL`, `TELEMARKETING`, `BANK_IMPERSONATION`, `DELIVERY_SCAM`, `TECH_SUPPORT_SCAM`, `GOVERNMENT_IMPERSONATION`, `HARASSMENT` and `OTHER`. These are user-report categories, not fraud verdicts: even an explicit category such as `BANK_IMPERSONATION` remains untrusted until moderation and must never be presented as verified identity or confirmed fraud.

Moderation is explicit and separate from public reporting. `GET /v1/moderation/pending` exposes only the HMAC phone fingerprint and bounded aggregate counts to an authenticated administrator. `POST /v1/moderation/decision` requires an independent `MODERATION_API_KEY`: `APPROVE` atomically consumes one matching pending signal and increments the trusted reputation once; `REJECT` consumes the pending signal without changing live reputation. The moderation key is server-side only and must never be embedded in Android or public JavaScript. Prefer a dedicated server-side `PUBLIC_REPORT_PEPPER`; when it is absent, the service derives a domain-separated sub-secret from the existing server-side `PHONE_HASH_PEPPER`. No report secret is sent to Android. Raw phone numbers are parsed transiently and are not persisted by this module.

The trusted `POST /v1/report-call` endpoint remains server-to-server and still requires `X-Report-Key`.


## Collective Defense Intelligence V1

The deployed API also exposes a privacy-preserving technical-indicator reputation surface for the first Collective Defense production slice.

Endpoints:

- `POST /v1/intelligence/lookup` — read-only reputation lookup for `DOMAIN`, `URL`, `EMAIL` and `SHA256`; when the server-side pepper is configured, the response also returns the opaque HMAC `indicator_fingerprint` used by the private client watch;
- `POST /v1/intelligence/lookup-fingerprint` — read-only recheck by `indicator_type + indicator_fingerprint`, intended for client-side watch refresh without retaining or retransmitting the original indicator value;
- `POST /v1/intelligence/report-public` — low-trust public report, pending only;
- `POST /v1/intelligence/report` — authenticated server-to-server observation using `X-Report-Key`;
- `GET /v1/intelligence/moderation/pending` — authenticated moderation queue;
- `POST /v1/intelligence/moderation/decision` — authenticated promotion/rejection using `X-Moderation-Key`.

### Privacy and truth-state guarantees

- raw indicator values are parsed transiently but are not persisted in the community reputation keys;
- persisted indicator identity uses HMAC-SHA-256 with the server-only `INDICATOR_HASH_PEPPER`;
- public fingerprint rechecks accept only 64-hex HMAC fingerprints; the fingerprint space is not an authorization token and never enables graph/exposure writes;
- `reputation_ttl_ms` is the Redis TTL remaining on the live reputation record; a missing, non-positive or impossible TTL degrades the result instead of fabricating freshness;
- a public report never changes live reputation before moderation;
- one accepted observation produces only `OBSERVED`, never a global block;
- community reputation alone always returns `enforcement_allowed: false`;
- `UNKNOWN` means insufficient evidence, not safe;
- email/domain/URL reputation is technical intelligence and never proves the identity or intent of a person;
- the V1 does not upload or retain private message bodies.

This is intentionally a bounded production slice. Threat Graph clustering is implemented separately in V2. The Android client can use the public reputation lookup, moderated public reporting and fingerprint-only local watch without embedding server credentials; that client path must still have exact-commit Android CI, release-artifact and physical-device evidence before it is described as operational for customers. End-user/device-authenticated exposure notifications, cross-channel campaign fusion and signed mobile threat bundles remain separate milestones and must not be represented as already operational.


## Collective Defense Threat Graph V2

The V2 production slice adds a bounded, authenticated IOC relationship graph on top of the V1 reputation service.

Endpoints:

- `POST /v1/intelligence/relationships/report` — server-to-server only, requires `X-Report-Key`;
- `POST /v1/intelligence/graph/lookup` — authenticated one-hop graph lookup, bounded to 25 neighbors.

Supported relationship classes are deliberately narrow: `REFERENCES`, `REDIRECTS_TO`, `DELIVERS_FILE`, `SHARES_INFRASTRUCTURE` and `SAME_CAMPAIGN_CANDIDATE`.

### V2 truth-state and privacy invariants

- graph nodes use HMAC indicator fingerprints; raw indicator values are normalized transiently and are not persisted by graph keys;
- public/community reports cannot create graph relationships;
- each relationship has a TTL, first/last seen timestamps, observation count and maximum evidence strength `E1..E4`;
- directional relationships preserve direction; explicitly symmetric relationships do not;
- `SAME_CAMPAIGN_CANDIDATE` can produce only a deterministic candidate-cluster fingerprint when evidence is at least E2;
- candidate clusters are not stable campaign IDs and are not a confirmed malicious campaign;
- graph relations never increment V1 reputation and always return `enforcement_allowed: false`;
- graph lookup is bounded and authenticated to avoid exposing the internal intelligence graph to public clients;
- the graph never attributes a technical indicator to a natural person.

Stable campaign identities, multi-hop fusion, source-diversity quorum, contradiction handling and automatic campaign confirmation remain future milestones and must not be represented as operational in V2.

## Collective Defense Exposure Evidence V1

This slice adds an authenticated server-to-server exposure index for retroactive matching without exposing a public device-history API.

Endpoints:

- `POST /v1/intelligence/exposures/report` — records that one pseudonymized subject encountered one technical indicator on a declared channel;
- `POST /v1/intelligence/exposures/lookup` — checks a bounded list of candidate indicators against the same pseudonymized subject.

Supported channels are `CALL`, `SMS`, `MMS`, `EMAIL`, `WEB`, `SOCIAL` and `FILE`.

### Exposure privacy and truth-state invariants

- both routes require the dedicated server-only `EXPOSURE_API_KEY` via `X-Exposure-Key`; there is no public exposure-write route;
- the caller must supply a high-entropy opaque subject token rather than a raw device/account identifier;
- the subject token is HMAC-pseudonymized with the dedicated server-only `EXPOSURE_HASH_PEPPER`, domain-separated before use;
- Redis exposure keys use a second domain-separated record HMAC over subject + IOC; there is no subject-prefixed exposure index to enumerate;
- indicator values are normalized transiently and persisted only as HMAC fingerprints;
- no message body, attachment payload, URL body content, phonebook data or social-message content is stored by this module;
- repeated observations of the same subject/indicator/channel are deduplicated for one hour and nonce replay is rejected for 24 hours;
- observations are stored in an age-bounded Redis sorted set: every accepted write removes entries older than 30 days, and lookup reads only the current 30-day window;
- each exposure record is also cardinality-bounded to 6,000 observations, and lookup fetches at most one item beyond that limit to fail closed on unexpected overflow;
- the exposed remaining TTL is derived from the oldest observation still contributing to the match, not from the Redis key TTL;
- lookup is subject-scoped and bounded to 50 candidate indicators; it does not enumerate subjects that encountered an indicator;
- `UNAVAILABLE` is distinct from `NONE` when Redis is disabled or degraded;
- `/health/ready` reports `exposure_intelligence: available` only when both dedicated Exposure secrets are configured; otherwise it reports `degraded` without pretending the feature is operational;
- an exposure match is evidence of contact with an indicator, not proof of compromise, identity or malicious intent;
- exposure evidence never authorizes automatic enforcement.

This is an internal foundation only. Direct mobile authentication, per-device notification delivery, private-set-intersection style lookup, stable campaign identities, source-diversity quorum and signed threat bundles remain separate release milestones.

