# Corrections de vérité produit — P1-3

Statut : **COMMERCIAL NO-GO**. Aucune ligne n’est commercialisable. « Testé sur Android » n’a pas été exécuté ici, y compris pour le Phone Core. Le hors-profil V1 n’efface pas un défaut réel : une fonction hors V1 peut quand même être incomplète dans le graphe complet.

Branche : `cursor/v1-product-truth-20261125-d8fe`, prise sur `integration/sentinel-v1-commercial-20261118` (`113862500`), pas sur la tête de #1772. #1772 est `fix/v1-phone-core-p1-20261119` (filtrage, écran verrouillé). La mélanger aurait ajouté 22 commits de Phone Core à un correctif de registre. Cette branche ne modifie ni MMS, ni Room, ni `SettingsStore`, ni `CallBlocklistStore`.

## Test

`scripts/v1-commercial-surface-truth.test.js`

Avant les correctifs, commit `42529204a` :

```
not ok 3 - le registre ne présente pas les capacités exclues comme disponibles client
  phone_core_android sans commercial_v1_exposed
not ok 4 - le statut généré distingue l’exclusion V1 d’un simple manque de validation
  Android public-source OSINT feeds restait IMPLEMENTED_NOT_VERIFIED
not ok 5 - les textes publics et l’écran VPN ne disent pas que l’exclu est opérationnel
  LIVE INTELLIGENCE encore présent
# tests 5
# pass 2
# fail 3
```

Les deux succès déjà verts concernaient le graphe commercial et le manifeste V1 : ils excluaient déjà ces surfaces. Le défaut était l’annonce, pas une route V1 oubliée.

Après `9021ac8f9` et `ee6eadd47` :

```
node --test scripts/v1-commercial-surface-truth.test.js scripts/check-product-capabilities.test.js scripts/generate-public-product-truth.test.js
# tests 25
# pass 25
# fail 0
```

`node scripts/check-product-capabilities.js` : PASS, 17 capacités. `node scripts/render-product-capabilities.js --check` : current.

`node scripts/check-android-ui-consistency.js` échoue déjà sur `113862500`, sans nos fichiers : marqueurs SMS absents dans `SmsComposeActivity.kt`. Non corrigé ici. Ce n’est pas devenu un simple avertissement.

Gradle, lint et S24+ : non exécutés.

## Corrections

| Correction | Fichier | Cause | Commit |
| --- | --- | --- | --- |
| Champ `commercial_v1_exposed` et bloqueur `-PsentinelCommercialV1=true` | `config/product-capabilities.json` L59, L78, L117, L136, L287, L645, L677, L698 | `configured: true` produisait `IMPLEMENTED_NOT_VERIFIED`, lu comme « presque prêt », alors que le profil V1 ne montre pas la surface. Le code hors V1 n’est pas déclaré fonctionnel pour autant. | `9021ac8f9` |
| Statut `EXCLUDED_FROM_COMMERCIAL_V1` | `scripts/render-product-capabilities.js` L14 et L86 ; `docs/PRODUCT_CAPABILITY_STATUS.md` L10–L25 ; `public/product-capabilities.json` ; `public/product-status.html` | Le rendu ne distinguait pas l’exclusion du profil et un simple manque de validation. | `9021ac8f9` |
| Sourcil « LIVE INTELLIGENCE » | `public/threat-intelligence.html` L16 | Le titre présentait une veille live alors que la page affiche « NON VÉRIFIÉ » et n’a pas été exécutée. | `ee6eadd47` |
| « plateforme Web opérationnelle » | `public/conference-presse.html` L19 | La vitrine statique était appelée opérationnelle. | `ee6eadd47` |
| Offre Free qui listait System Doctor et l’audit comme inclus | `public/pricing.html` L47 | Ces écrans sont hors graphe commercial et non qualifiés. | `ee6eadd47` |
| « Le scan manuel reste gratuit » et FIMI sans réserve | `public/roadmap.html` L85–L86 | Le scan ne nettoie pas le système. DISARM-FR n’est pas importé. | `ee6eadd47` |
| « Fondations déjà actives » et arbitrage Mesh | `VpnScreen.kt` L99, L140, L149 | L’écran n’a pas de bouton de connexion et le Mesh n’y est pas appelé. « Client intégré » est conservé : le contrôle `check-android-ui-consistency.js` l’exige pour interdire « Client prêt ». | `ee6eadd47` |
| Description OSINT | `strings.xml` L80 | Le texte décrivait une veille active sans dire qu’elle est hors V1 et non qualifiée. Le défaut de non-qualification appareil reste. | `ee6eadd47` |
| Aperçu « Exposition » en validation | `public/android-preview.js` L80 | « En validation » laissait croire que l’écran est dans le candidat V1. | `ee6eadd47` |
| Matrice des colonnes demandées | `audit/v1-commercial/FEATURE_MATRIX.md` | Les exclusions existaient, sans colonnes code / APK / activé / accessible / Android / commercialisable. | `ee6eadd47` |

## Ce qui n’est pas réparé

- Phone Core : toujours non qualifié sur appareil, `customer_available` false, `runtime_verified` false. NO-GO.
- Passerelle VPN absente, poids Qwen absents, taxonomie DISARM absente, Mesh sans appel d’écran : défauts réels, seulement rendus visibles.
- Fraîcheur des flux CERT-FR : non appelée.
- `SmsComposeActivity.kt` : échec préexistant du contrôle UI, laissé à l’autre chantier.
- S24+, fichiers locaux `ui/design/Sentinel*.kt` et `app/schemas/` du clone `integration/d1-visual-phonecore-20261029` : non examinés.

## Contre-audit avec `fix/v1-p0-v25-v26-20261125`

`git ls-remote origin refs/heads/fix/v1-p0-v25-v26-20261125` ne renvoie aucune référence le 2026-11-25. La branche n’est pas sur le remote consulté. Aucun diff n’a donc pu être calculé. Cette branche-ci ne touche pas les fichiers MMS, Room, `SettingsStore`, `CallBlocklistStore`, Gradle ni les manifestes. Le seul fichier partagé plausible si l’autre agent édite les chaînes est `strings.xml` L80. Il n’y a pas d’écrasement : les branches ne sont pas fusionnées.
