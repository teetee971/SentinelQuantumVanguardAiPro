# SOC Automation Policy Kernel

## Statut

**Partiel et testé par code.** Ce noyau évalue l'éligibilité d'une demande d'automatisation SOC. Il ne constitue pas un SOC de production et n'exécute aucun effet privilégié.

## Frontière de confiance

Toute demande entre comme non fiable. Elle doit fournir une action connue, un mode explicite, une version de politique, une cible et une source déclarée fiable. Une valeur inconnue est refusée.

### Traitements passifs

- normalisation d'événement ;
- déduplication ;
- enrichissement d'alerte ;
- création de dossier ;
- proposition de playbook.

Ils peuvent être orientés vers un worker déterministe, sans que le noyau effectue lui-même l'action.

### Actions à fort impact

- confinement d'un endpoint ;
- blocage d'un indicateur ;
- quarantaine d'un artefact ;
- désactivation d'un compte.

Le mode simulation ne peut produire qu'un passage vers la simulation. Une demande d'exécution exige :

1. une cible explicitement autorisée ;
2. une simulation déclarée sûre ;
3. une référence de retour arrière ;
4. des approbations authentifiées par une couche externe ;
5. deux approbations humaines valides, distinctes et liées à l'action, la cible et la version de politique.

Même lorsque ces conditions sont satisfaites, le résultat est uniquement un transfert vers `authorizeBoundedOperation`. Le noyau ne possède aucun callback d'exécution et retourne toujours `side_effect_performed: false`.

## Interdictions permanentes

Les actions d'exploitation, accès aux identifiants, persistance, exfiltration ou interruption de service sont refusées, y compris en mode simulation dans ce périmètre SOC. Un LLM, une automatisation ou une fixture ne peut pas produire une approbation humaine.

## Limites

- `source_trusted`, `approvals_authenticated` et `simulation_safe` sont des assertions d'entrée : leur authentification cryptographique doit être réalisée avant l'appel.
- Ce noyau ne remplace pas la vérification des preuves Ed25519, l'anti-rejeu, la machine d'état d'exécution ni l'impact preflight existants.
- Aucun connecteur SIEM/EDR, système de tickets, astreinte ou stockage d'incident n'est livré par ce changement.
- Aucun MTTD, MTTR ou taux de faux positifs n'est revendiqué sans exécutions opérationnelles observées.

## Pipeline passif signé

Le module `security/soc/passive-pipeline.js` accepte au maximum 100 événements par lot et 32 Kio de charge utile canonique par événement. Il vérifie une signature Ed25519 contre une liste d'émetteurs autorisés, contrôle la fenêtre de validité, consomme l'identifiant dans un garde anti-rejeu, puis déduplique les observations identiques.

La sortie contient uniquement des dossiers déterministes : identifiants de source, classe mono/multi-source et aucune proposition automatique d'action. Le pipeline ne persiste rien, ne contacte aucun système externe et retourne toujours `privileged_action_requested: false` et `side_effect_performed: false`.

L'adaptateur en mémoire utilisé par les tests n'est pas acceptable en production. Une mise en service exigera un stockage durable avec consommation atomique, une rotation/révocation réelle des clés et un registre de sources exploité.

## Prochaine étape

Créer un schéma d'événement normalisé signé, puis un pipeline passif borné `ingestion -> validation -> déduplication -> enrichissement -> dossier`. Les actions à fort impact resteront hors du worker passif et passeront par la chaîne d'autorisation liée existante.
