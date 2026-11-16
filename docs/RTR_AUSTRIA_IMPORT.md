# Autriche : import local des attributions RTR

## Couverture livrée

Le site recherche les plages RTR en local dans le navigateur, après chargement à la demande d’un index statique. La recherche reste gratuite. Le numéro recherché n’est pas envoyé à RTR, à Render ou à Upstash pour cette consultation.

Le lot fourni le 15 septembre 2026 contient 74 014 lignes géographiques et de services. L’index en retient **73 749** : 76 préfixes de sélection d’opérateur et 189 plages de routage sont exclus, car ils ne doivent pas être assimilés à des numéros internationaux d’appelants. Les 1 022 zones sont utilisées pour contrôler les correspondances géographiques.

Résultat affiché : titulaire de l’attribution et identifiant RTR lorsqu’ils sont publiés, plage, catégorie originale, zone de numérotation éventuelle, statut, date d’import et sources. Les mentions administratives ne deviennent jamais des noms d’opérateurs. Treize plages sont imbriquées dans une autre : leur intersection produit un résultat ambigu, sans choisir arbitrairement un attributaire.

## Limites à conserver dans l’interface

- Attribution de ressource, pas identité de l’abonné ou de l’appelant. Aucun enrichissement d’adresse personnelle.
- L’opérateur actuel après portabilité et l’origine réelle d’un appel usurpé ne sont pas déterminés.
- Une plage « non attribuée » décrit ce fichier, pas nécessairement l’état présent du numéro, et ne prouve pas une fraude. Aucun score ou blocage n’est déclenché par cette donnée.
- Les CSV ne donnent pas de date de publication. `sourcePublishedAt` reste `null` ; `generatedAt` est uniquement la date de fabrication de l’index.
- Comparaison sur la longueur publiée uniquement. Pas de remplissage avec des zéros, de suppression d’extensions ou d’interprétation automatique des numéros raccourcis. Une absence de correspondance n’invalide pas le numéro.
- Les codes courts et étoilés nécessitent un parcours séparé. Les paramètres réseau SKP ne sont pas des numéros d’appelants.
- Le navigateur peut conserver un ancien index via le cache du site ; sa date d’import reste visible. Il ne s’agit pas d’une requête RTR en temps réel.
- L’index n’est pas encore synchronisé avec Android ; ce lot concerne le site/PWA.

RTR décrit les longueurs géographiques et leurs exceptions dans sa [FAQ officielle](https://www.rtr.at/TKP/was_wir_tun/telekommunikation/nummerierung/FAQNum/geografische_rufnummern.de.html). Les [droits individuels et exceptions](https://www.rtr.at/TKP/service/rufnummernsuche/Ergebnis/SingleGeoNumbers.de.html) doivent être vérifiés auprès de RTR, notamment en cas d’attribution partielle.

## Provenance des six fichiers fournis

Les fichiers originaux ne sont ni modifiés ni publiés avec ce code. Les sommes SHA-256 identifient exactement le lot fourni, sans certifier sa date de publication chez RTR.

| Fichier | Lignes | Utilisation |
|---|---:|---|
| tn-geo.csv | 64 662 | Plages géographiques |
| tn-dienste.csv | 9 352 | Services ; 265 lignes techniques exclues |
| tn-ortsnetze.csv | 1 022 | Validation des zones géographiques |
| tn-kurz.csv | 157 | Analysé, codes courts non importés |
| tn-stern.csv | 34 | Analysé, codes étoilés non importés |
| tn-skp(1).csv | 140 | Analysé, paramètres réseau non importés |

Les trois entrées utilisées ont leurs SHA-256 et nombres de lignes dans `public/data/rtr-numbering.json`. Empreintes des trois fichiers non importés :

```text
tn-kurz.csv     4063a9d9b80fe43689e537598c2735be99e8dca496026ef4c8a301c58b6812be
tn-stern.csv    8a043922af2aee3646d06b6bec7056e58016bd445f1a2f4bd87837306d8e5083
tn-skp(1).csv   75028ef4077bde46e385b8f97089130b0b233eec26cc87cbf4a257cb661a3292
```

## Reconstruction et mise à jour

Récupérer les CSV depuis les liens Open Data de la [page officielle RTR](https://www.rtr.at/TKP/service/rufnummernsuche/Rufnummernsuche.de.html). Conserver les zéros initiaux et l’encodage UTF-8 ; ne pas réexporter les numéros comme valeurs numériques dans un tableur.

```sh
npm run update:rtr-numbering -- \
  --geo /chemin/tn-geo.csv \
  --services /chemin/tn-dienste.csv \
  --areas /chemin/tn-ortsnetze.csv \
  --generated-at 2026-09-15T19:28:08Z
npm run test:phone-intelligence
npm run build
```

Pour un nouveau lot, utiliser la date réelle d’import ou omettre `--generated-at`. Ne renseigner `--source-published-at YYYY-MM-DD` que si cette date est documentée par la source. Les mêmes octets et la même date d’import produisent le même JSON.

Le parseur vérifie encodage, schémas exacts, nombre de colonnes, bornes, longueurs, identifiants, catégories administratives connues et cohérence des zones. Les fichiers sont limités à 8 Mio, l’index à 4 Mio. L’écriture finale remplace atomiquement l’index après validation ; une erreur laisse la version précédente en place. Le client limite également le téléchargement et permet une nouvelle tentative après échec.

Avant publication d’un nouveau lot : inspecter le diff de provenance et les exceptions, adapter les assertions relatives au lot de référence seulement sur preuve, passer les tests et la CI sur une PR. Il n’existe pas encore de synchronisation automatique RTR. Les tests couvrent chaque borne de l’index, les chevauchements, les statuts administratifs, les fichiers invalides et l’échec de chargement.

## Réutilisation

Attribution : **RTR-GmbH – data.rtr.at**. Les [conditions Open Data RTR](https://www.rtr.at/rtr/service/opendata/OD_Nutzungsbedingungen.de.html), consultées le 15 septembre 2026, décrivent la réutilisation des données et demandent cette attribution. Ne pas leur substituer une licence Creative Commons issue d’un autre jeu RTR. L’index est une transformation des CSV fournis, sans affiliation ni validation de Sentinel par RTR.
