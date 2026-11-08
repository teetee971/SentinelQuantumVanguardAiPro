# GeoIntel — prévisualisation publiée, accès requis pour la qualifier

Projet confirmé : `sentinelquantumvanguardaipro`, compte `78642e56f72fff94c78e1ef87cb589a7`.
Console propriétaire : https://dash.cloudflare.com/78642e56f72fff94c78e1ef87cb589a7/pages/view/sentinelquantumvanguardaipro

## Publication réelle observée

Le contrôle GitHub de l’application **Cloudflare Pages**128688562963 annonce `Deployed successfully`, conclusion `success`, terminé le8novembre2026 à09:23:05UTC pour le commit `27388cd36ed56a5c8657479b1ebb6942d663cfa5`. Il s’agit d’un déploiement automatique de la branche isolée, pas d’un workflow GitHub Actions lancé par l’agent ni d’une fusion main.

- Prévisualisation immuable : https://7518ecda.sentinelquantumvanguardaipro.pages.dev/public/geointel.html
- Alias de branche : https://fix-geointel-multisource.sentinelquantumvanguardaipro.pages.dev/public/geointel.html
- Contrôle propriétaire : https://dash.cloudflare.com/?to=/78642e56f72fff94c78e1ef87cb589a7/pages/view/sentinelquantumvanguardaipro/7518ecda-c230-4b71-81b6-c2133919fbde

Preuve : `CLOUDFLARE_DEPLOYMENT_CHECK.json`. Les fichiers applicatifs sont identiques à ceux du code local qualifié `3724f695` ; le diff jusqu’à27388cd ne contient que `qualification/geointel/*`. Cela ne prétend pas que deux archives compilées avec des marqueurs de commit différents ont le même SHA-256.

## Blocage externe vérifié

Huit requêtes HTTPS ont été réellement exécutées sur la prévisualisation : marqueur de déploiement, page et six routes `/api/geointel/{source}?days=7`. Elles ont répondu HTTP403, corps `error code: 1010`. Le navigateur Chromium standard avec TLS vérifié a ensuite navigué vers la page : il arrive sur **Sign in · Cloudflare Access**, avec une adresse email à saisir et aucun élément GeoIntel. Aucun email, mot de passe, code ou jeton n’a été saisi, demandé au fournisseur ou partagé. Aucun contournement de protection ni changement de règle Cloudflare n’a été tenté.

Preuves horodatées : `CLOUDFLARE_PREVIEW_HTTP.json`, `CLOUDFLARE_PREVIEW_BROWSER.json` et capture dans les preuves externes. Les paramètres temporaires de la redirection de connexion ont été retirés. La présence d’une page de connexion ne qualifie pas la console applicative.

**État exact : prévisualisation annoncée déployée par Cloudflare ; qualification applicative en ligne BLOCKED par Cloudflare Access.** Aucun accès au compte de déploiement ou au navigateur propriétaire authentifié n’est exposé à cet agent. La production n’a pas été modifiée. Les états runtime/customer/production du registre restent désactivés.

## Dernière qualification à effectuer avec un accès autorisé

Ouvrir la prévisualisation dans un navigateur déjà autorisé par le propriétaire, sans transmettre de code ni jeton au chat. Lire `deployment-meta.json` et son SHA, puis contrôler les six états, les six routes API, les liens officiels, les coordonnées manquantes, l’actualisation et les dates. Tester panne d’une source, mode hors ligne avec le service worker actif, annulation, erreurs de console et affichage mobile390px. Conserver une preuve expurgée de toute information de connexion. Vérifier les bindings ASSETS et le relais réel, qui restent inaccessibles derrière la protection actuelle.

Ne pas publier en production ni activer les états publics sur la seule foi du contrôle «deployed successfully». La publication en production reste une opération distincte ; aucune fusion main n’est effectuée.

## Paquet local de secours

`/workspace/qualification-evidence/geointel-multisource/Sentinel-GeoIntel-3724f695-web.tar.gz`

SHA-256 : `c46c92ac652c32109caaff1b833c3148d10ca32d1f9204be16941554155db002`.

Le répertoire de publication contient directement `_worker.js`. Si un redéploiement est nécessaire avec un accès Cloudflare sécurisé configuré hors du chat : vérifier `wrangler whoami`, le projet et la branche de production, puis utiliser une branche de prévisualisation distincte. Un accès administrateur complet n’est pas nécessaire pour simplement consulter une prévisualisation déjà protégée ; une session Cloudflare Access autorisée suffit pour la qualifier.
