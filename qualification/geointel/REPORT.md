# GeoIntel multisource — livraison du 8 novembre 2026

**Implémenté et qualifié localement ; non déployé.** Console web existante, pas un nouveau module Android natif. Branche `fix/geointel-multisource`, parent `0e280e6068544e5ad23962a5e31b45f943532296`. Code livré/testé `3724f695ef614e8a7e4c6e307cf0a094c8e2ecd2`. Aucun APK reconstruit, installé ou prétendu fournir cette console en natif ; v22 et artefacts Doctor préservés. Aucune fusion main ni publication.

## Couverture réelle

| Collecteur | Données et limites |
|---|---|
| USGS | Séismes mondiaux M2,5+, fenêtres1/7/30j, point et timestamps officiels ; seul le statut source reviewed permet verified |
| NASA EONET | Événements naturels signalés : incendies/volcans, tempêtes et autres phénomènes ; dernier point daté publié, pas de points chauds géopolitiques inventés ; géométries non ponctuelles rejetées explicitement |
| NWS | Alertes actives américaines/territoires, pas météo mondiale ou archive exhaustive ; zones non cartographiées sans point inventé |
| IODA Georgia Tech | Observations/anomalies réseau, pas preuve indépendante d'une coupure ; localisation ASN/pays en texte, sans coordonnées ; limite50 et couverture dégradée quand atteinte ; score relatif non converti en confiance |
| ONU paix/sécurité | Publications, pas un recensement d'incidents de conflit confirmés ; date de publication et lien source, liste uniquement |
| OFAC | Publications d'actions récentes américaines, pouvant inclure licences/FAQ/radiations ; pas une liste consolidée mondiale des sanctions ou une décision juridique calculée par Sentinel ; liste uniquement |

Toutes les sources gardent leur identité, leur date publiée et leur lien. Une acquisition ne crée pas de date de détection, position ou confiance manquante. Les observations IODA n'ayant pas de timestamp d'actualisation gardent `detectedAt=null` et une fraîcheur inconnue. Les dates futures/incohérentes, identifiants absents et liens non officiels sont refusés. Une sévérité absente reste inconnue et un risque sans confiance n'est pas calculé. Les intervalles actifs peuvent intersecter la fenêtre même si leur début est plus ancien.

Six états indépendants HEALTHY/DEGRADED/DOWN ; panne d'un collecteur sans suppression des résultats valides des autres. Aucun remplacement automatique par de faux événements de démonstration. Maximum500 événements visibles, carte et panneaux à hauteur bornée, panneaux défilants, petits contrôles de sélection avec labels cliquables et coordonnées réelles dans l'inspecteur.

## Architecture et corrections vérifiées

- `_worker.js` Cloudflare Pages advanced mode : uniquement `/api/geointel/{source}` avec source fixe et fenêtre1/7/30. Pas de paramètre URL libre, cookies/authorization du navigateur, redirection d'egress ou secret. Les autres routes passent à `env.ASSETS.fetch`.
- Toutes les acquisitions de la console, y compris USGS, sont same-origin : compatibles avec le CSP existant, sans élargir `connect-src`.
- Limite5MiB sur les octets effectivement lus, délai serveur7s/client8s, MIME contrôlé, aucune attente indéfinie de la promesse de cancellation.
- EONET a réellement répondu avec du JSON mais un Content-Type RSS/XML : seule cette source accepte ce libellé erroné, puis son schéma JSON doit être reconnu. Aucune acceptation générale d'HTML/XML comme JSON.
- ONU/OFAC n'exposaient pas d'Access-Control-Allow-Origin lors des acquisitions ; le relais évite de dépendre d'un accès navigateur direct.
- Réponses API et requêtes navigateur no-store ; service worker2.4.1 ignore GeoIntel pour la lecture et l'écriture de cache. Pas de vieilles données en cache présentées comme une nouvelle acquisition saine lors d'une panne.
- XML traité comme données via DOMParser, DTD/entities refusées, aucune insertion des titres comme HTML ; liens officiels HTTPS seulement. L'inspecteur reste textuel et utilise `noopener noreferrer`.
- Build web copie le worker et les modules nécessaires dans `frontend/dist`. L'archive finale contient un marqueur lié au SHA de code. Ce paquet n'a pas été déployé et son import/binding ASSETS sur le projet Cloudflare réel reste à qualifier.

## Exécutions et preuves

Linux cloud, Node24.22.1, Chromium headless réel avec profil temporaire isolé. Les acquisitions HTTPS officielles ont été exécutées le7novembre ; détails horodatés dans `PROVIDER_HTTP.json`. Les tests et rejeux ont été exécutés les7/8novembre. Les tests navigateur utilisent ces réponses capturées via le vrai code du relais local, **pas un déploiement ou un live E2E de production**.

| Vérification | Résultat réellement exécuté |
|---|---|
| GeoIntel + service worker |80 tests, 0 échec, 0 ignoré |
| Régression capacités / contrat public / liens |33 tests, 0 échec, 0 ignoré |
| Cohérence produit Android |69 tests, 0 échec, 0 ignoré |
| Build web final lié au SHA |8 éléments copiés, 0 erreur |
| Métadonnées Android générées |compileDebugKotlin réussi en27s ; aucune modification fonctionnelle native ni nouvel APK revendiqué |
| Registre / isolation / secrets |Contrôles réussis ; runtime/customer GeoIntel restent false |
| Providers HTTP |Six réponses200 capturées ; IODA a d'abord dépassé15s, puis répondu avec limite50/extendWindow0 ; en-tête incorrect EONET observé et traité |
| Chromium : sources réelles capturées |Six états affichés, résultats géolocalisés/non géolocalisés séparés ; XML réellement parsé, source inspectable |
| Chromium : pannes |OFAC503 simulé sans perdre les autres sources ; réseau entièrement offline =>6DOWN et aucun événement en cache/démo présenté comme frais, service worker réel activé |
| Chromium : ergonomie |1440px et390px, absence de débordement horizontal ; carte bornée avec500résultats ; capture finale inspectée |
| Origine publique / déploiement Cloudflare |**NON EXÉCUTÉS** |

L'acquisition capturée du7novembre donnait4HEALTHY/2DEGRADED (NASA85acceptés/4géométries rejetées, IODA50limités). Le rejeu du8novembre donne aussi USGS DEGRADED car son instantané a vieilli : une nouvelle lecture ne réinitialise pas la fraîcheur source. Les nombres d'événements filtrés varient avec l'horloge, sans date inventée.

### Commandes exactes

Depuis `/workspace/sentinel-play-ready` :

```bash
node --test public/geointel-multisource.test.js
# Rouge initial avant implémentation.
node --test public/geointel-*.test.js public/sw.test.js
node --test scripts/check-product-capabilities.test.js scripts/generate-public-product-truth.test.js scripts/public-product-contract.test.js scripts/check-static-links.test.js
npm run test:android-product-truth
node scripts/check-product-capabilities.js
node scripts/render-product-capabilities.js
node scripts/generate-public-product-truth.js
node scripts/check-sentinel-isolation.js
node scripts/check-credential-hygiene.js
CF_PAGES_COMMIT_SHA=3724f695ef614e8a7e4c6e307cf0a094c8e2ecd2 npm run build
node qualification/geointel/browser-test.mjs
source /workspace/toolchains/env.sh
gradle -p native-android-app :app:compileDebugKotlin --offline --no-daemon --max-workers=1 --console=plain
```

Le test Chromium attend les captures `*.payload` dans `/workspace/qualification-evidence/geointel-multisource`, la disponibilité de `/usr/bin/chromium`, puis démarre un serveur HTTP local, le vrai relais avec réponses capturées, le CSP du dépôt et le vrai SW. Les données de capture sont des flux publics, jamais des données du téléphone. Le test de panne/fonctionnement offline est injecté ; il ne doit pas être présenté comme une panne réellement observée chez le fournisseur. Les tests ne simulent pas une approbation juridique des données.

### Régressions trouvées et traitées

- MIME EONET erroné ; exception strictement bornée à EONET + validation de schéma.
- IODA initial trop lent ; limite50 explicite, timeout conservé, pas de promesse d'exhaustivité.
- CSP bloquant USGS direct ; test rouge puis passage de la console au relais same-origin.
- SW et cache HTTP risquant de masquer une panne ; tests rouges, bypass/no-store, preuve navigateur offline avec contrôleur SW réel.
- Cancellation du flux pouvant bloquer un rejet de taille ; cas reproduit rouge avec cancellation qui ne termine jamais, puis cleanup non bloquant.
- Ancien contrat de version SW2.4.0 : mis à jour intentionnellement en2.4.1 pour invalider les anciens caches, sans retrait du contrôle.
- Première injection de panne du harness servie par le cache navigateur : cache désactivé pour l'injection et no-store désormais imposé par le produit ; preuve finale offline explicite.
- Serveur de test initial ne servait pas `/` pour le précache SW ; harness corrigé, activation avec timeout et vérification du contrôleur effectif.
- Avec500cartes, la grille d'origine étirait le canvas jusqu'à54247px : assertion navigateur rouge puis CSS borné, listes défilantes et assertion <=1200px. La capture finale fait1425x1449 au lieu de1425x54247. Les titres/positions et preuves restent accessibles, sans masquer les limites des sources.

Revue indépendante : trois problèmes concrets relevés (CSP, cache, cancellation), corrections relues, aucun problème important restant signalé. Les logs bruts sont dans le dossier externe ; les copies `logs/` ne normalisent que les espaces finaux.

## Artefacts

[ARTIFACTS.json](ARTIFACTS.json) donne les chemins exacts, tailles et SHA-256. Le build source antérieur edf33648 est supersédé par le correctif de hauteur3724f695 ; seul le paquet ci-dessous est la livraison finale.

- `/workspace/qualification-evidence/geointel-multisource/Sentinel-GeoIntel-3724f695-web.tar.gz` : `c46c92ac652c32109caaff1b833c3148d10ca32d1f9204be16941554155db002`.
- `/workspace/qualification-evidence/geointel-multisource/GeoIntel-provider-browser-evidence-final.zip` : `bdf39d8b5b5ecb49cda039b775ab15793747e22275bc8362cb9aa4e691929a33`.

Le premier contient `dist/`, worker, modules, CSP et `deployment-meta.json` lié au SHA3724f695. Le second contient les réponses officielles capturées, métadonnées réseau, capture d'écran et résultats navigateur. Les archives ne sont pas committées ni liées aux sorties mutables du build. Aucun APK ni keystore nouveau.

## Étape externe restante

1. Confirmer le projet Cloudflare Pages et l'autorisation d'un déploiement de prévisualisation. Aucun accès/jeton de déploiement n'a été fourni ou utilisé.
2. Déployer le build sur ce seul canal autorisé, avec le worker advanced mode et binding ASSETS ; conserver main inchangé.
3. Tester les six `/api/geointel/{id}?days=7` depuis l'origine réelle : MIME, taille, timeout, erreurs, no-store, absence d'en-têtes d'authentification transmis et import du worker. Contrôler la cadence/les conditions de réutilisation des fournisseurs.
4. Rejouer le navigateur réel contre cette origine avec données réellement acquises, CSP et SW, pannes et filtres ; enregistrer SHA/date/couverture. Ne pas compter les captures locales comme preuve de déploiement.
5. Seulement après preuve conforme au registre envisager runtime/customer availability. Aucun recensement exhaustif des conflits, des sanctions ou des coupures ne doit être annoncé.

## Cible Cloudflare confirmée

Le propriétaire a identifié le projet `sentinelquantumvanguardaipro`. L’accès de déploiement reste absent ; les deux contrôles HTTP publics exécutés le8novembre ont répondu403. Procédure exacte et paquet : [DEPLOYMENT.md](DEPLOYMENT.md). Statut en ligne : **BLOCKED**, aucun déploiement effectué.
