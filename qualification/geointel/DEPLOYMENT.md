# GeoIntel — déploiement restant

Projet confirmé par le propriétaire le 8 novembre 2026 : `sentinelquantumvanguardaipro`.
Compte Cloudflare : `78642e56f72fff94c78e1ef87cb589a7`.
Console : https://dash.cloudflare.com/78642e56f72fff94c78e1ef87cb589a7/pages/view/sentinelquantumvanguardaipro

## État vérifié

Le code et l’archive sont prêts pour une prévisualisation. Aucune session Cloudflare, identité de déploiement ou variable secrète n’est configurée dans cet environnement. Aucun outil de contrôle du navigateur partagé n’est exposé ; le statut du bureau est `enabled/unknown`. Le lien du projet identifie la cible mais ne fournit pas d’authentification.

Les deux requêtes publiques suivantes ont été tentées le 8 novembre et ont reçu HTTP403 depuis cet environnement :

- `https://sentinelquantumvanguardaipro.pages.dev/public/geointel.html`
- `https://sentinelquantumvanguardaipro.pages.dev/api/geointel/usgs?days=7`

Un403 ne prouve pas l’état du code déployé. Il empêche ici sa qualification publique.

## Paquet concret à prévisualiser

Archive : `/workspace/qualification-evidence/geointel-multisource/Sentinel-GeoIntel-3724f695-web.tar.gz`.
SHA-256 : `c46c92ac652c32109caaff1b833c3148d10ca32d1f9204be16941554155db002`.
Code : `3724f695ef614e8a7e4c6e307cf0a094c8e2ecd2` ; PR1760 reste en brouillon.
L’archive inclut `_worker.js`, les fichiers statiques et `deployment-meta.json` ; le répertoire de publication doit contenir directement `_worker.js`.

Avec un accès Cloudflare configuré de façon sécurisée hors du chat :

```bash
wrangler whoami
wrangler pages project list
wrangler pages deployment list --project-name sentinelquantumvanguardaipro
# Après extraction de l’archive dans un répertoire temporaire et contrôle de son SHA :
wrangler pages deploy CHEMIN_DU_DIST --project-name sentinelquantumvanguardaipro --branch geointel-qualification
```

Cette branche sert à la prévisualisation, sans fusion main. Vérifier d’abord dans le projet que `geointel-qualification` n’est pas la branche de production. Aucun jeton ne doit être collé dans le chat ou ajouté au dépôt.

## Qualification de la prévisualisation

1. Lire le marqueur `deployment-meta.json` et vérifier le SHA de code.
2. Vérifier les6routes `/api/geointel/{usgs,eonet,nws,ioda,un,ofac}?days=7` et leurs réponses bornées/no-store ; un fournisseur indisponible doit être DOWN, sans remplacement par une démo.
3. Ouvrir `/public/geointel.html`, contrôler CSP, service worker,6états, liens officiels et coordonnées manquantes non inventées.
4. Tester actualisation, annulation, panne d’une source, réseau coupé et affichage mobile390px.
5. Conserver URL exacte, horodatage, réponses, console navigateur et captures. Une prévisualisation validée ne prouve pas encore le déploiement de production.
6. Actualiser les états runtime/deployed/customer seulement après preuve réelle sur l’origine concernée.

Statut final de cette étape : **BLOCKED — accès Cloudflare absent et origine publique403**. Aucun déploiement revendiqué.
