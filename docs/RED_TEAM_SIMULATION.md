# Red Team Simulation — périmètre documenté

**Dernière mise à jour : 3 septembre 2026**

Ce document décrit un environnement de simulation défensive. Il ne constitue ni une garantie de sécurité ni une autorisation d'interagir avec des systèmes tiers.

## Périmètre

Le simulateur génère des scénarios, événements et indicateurs fictifs destinés aux tests, à la formation et à l'évaluation de contrôles. Les éléments générés doivent rester identifiables comme simulés.

L'utilisation doit être limitée à des environnements possédés ou explicitement autorisés.

## Principes de sécurité

- Aucun exploit réel ne doit être ajouté au simulateur.
- Aucun accès non autorisé ne doit être tenté.
- Aucun malware fonctionnel ne doit être distribué par le simulateur.
- Les données synthétiques ne doivent pas être présentées comme des incidents réels.
- Une simulation réussie ne prouve pas la couverture réelle d'un environnement de production.

## MITRE ATT&CK

Le framework MITRE ATT&CK peut servir à structurer les scénarios et à identifier les techniques simulées. La couverture affichée doit être calculée à partir des scénarios et événements effectivement exécutés ; elle ne doit pas être confondue avec une mesure globale de détection d'une organisation.

## IOCs et journaux

Les IOCs et événements générés par une simulation sont synthétiques. Les exportations JSON, CSV ou CEF doivent conserver un marqueur explicite indiquant leur origine simulée.

Les exemples utilisant des adresses TEST-NET ou d'autres valeurs réservées sont des exemples et ne constituent pas des indicateurs de compromission réels.

## Limites

Les métriques telles que nombre d'événements, techniques utilisées, durée ou couverture sont des métriques du scénario exécuté. Elles ne constituent pas des garanties de MTTD, de prévention ou de détection en production.

## Évolutions recommandées

- Ajouter des tests de non-exécution pour vérifier qu'un scénario ne lance aucune commande système dangereuse.
- Ajouter une validation stricte du champ `simulated` aux événements exportés.
- Interdire les destinations réseau arbitraires dans les scénarios.
- Ajouter des tests de limites sur la taille des scénarios et le nombre d'événements.
- Produire un rapport déterministe lié au commit et à la version du scénario.

## Référence de gouvernance

Tout nouveau scénario doit être revu avant intégration et accompagné de tests démontrant qu'il reste dans le périmètre de simulation.
