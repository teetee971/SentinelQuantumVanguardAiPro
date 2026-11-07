# Dépendances — 2026-11-06

L’API REST Dependabot refuse l’accès (403, Resource not accessible by integration). GraphQL retourne 0 alerte, même sans filtre ; les réponses brutes sont archivées avec la qualification initiale. L’état réel des six alertes GitHub reste **UNVERIFIED** : aucune fermeture ne peut être affirmée.

L’audit indépendant `npm audit --package-lock-only --json` identifie précisément **vite 8.3.1**, dépendance directe de développement dans `package-lock.json`, affectée par ces six avis :

| Avis | Gravité | Problème |
| --- | --- | --- |
| GHSA-698v-4r3x-3vcv | moderate | launch-editor: escapeCmdArgs() omits % escaping on Windows |
| GHSA-r825-w8mm-5pxw | moderate | launch-editor: Editor CLI flag misinterpretation causing arbitrary command execution for some editors |
| GHSA-587h-7wmj-h5pm | high | launch-editor: `fs.existsSync()` existence check can be bypassed on Windows |
| GHSA-9jrq-w75r-8gcw | low | Vite: Cross origin script execution through Vite custom middleware |
| GHSA-rq7h-c2jc-7f22 | moderate | Vite: Files outside `server.fs.allow` could be served when project paths match system paths on non-Windows machine |
| GHSA-vfpm-58rq-9qcg | moderate | Vite: First 4 bytes of files outside of `server.fs` setting is exposed by `?vite-wasm-instance` |

Plage vulnérable : 8.3.0 à 8.3.2 inclus. Correction : **8.3.3**, sans changement majeur. `package.json` exige maintenant ^8.3.3 et le lock résout 8.3.3. Les versions transitives sont conservées ; seule l’entrée Vite et sa contrainte rolldown évoluent. Le rolldown verrouillé satisfait la nouvelle contrainte.

Compatibilité : Node 24.22.1 satisfait les engines Vite (^20.19.0 ou >=22.12.0). Vite sert au développement/preview web ; l’APK Android n’embarque pas cette dépendance. Le build de production utilise le copieur statique existant. `npm ci --ignore-scripts`, Vite --version, démarrage HTTP local, `npm run build`, 13 tests frontend et 6 tests credential-hygiene réussissent. Le build a régénéré product-status.html ; ce résultat généré a été exclu du patch afin de respecter le gel fonctionnel.

Après correction : audit npm = **0 vulnérabilité**. Cela ne prouve ni la fermeture des alertes Dependabot ni l’absence de vulnérabilités Maven/Python. Les API GitHub doivent être revérifiées avec un accès autorisé aux alertes. Aucune mise à jour majeure, aucun audit fix automatique, aucune exécution GitHub Actions.
