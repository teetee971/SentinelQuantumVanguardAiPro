# SOC Live — périmètre réel

## Statut

`public/soc-live.html` est une interface web informative. Elle peut consulter des sources publiques de vulnérabilités lorsqu'elles sont accessibles depuis le navigateur. Cette page ne constitue pas un SOC opérationnel, un EDR, un antivirus ni une capacité de protection active.

## Sources référencées par l'interface

Le code de la page doit rester la source de vérité pour les endpoints réellement utilisés. La documentation ne doit jamais présenter une API comme connectée si son appel n'est pas présent et vérifiable dans le code actuel.

Les sources publiques actuellement prévues dans cette surface sont notamment :

- GitHub Security Advisories ;
- NVD/CVE lorsque l'accès réseau et les limites de l'API le permettent.

Les données distantes peuvent être indisponibles, limitées par le réseau, le navigateur, le CORS ou le rate limiting. Une absence de données ne doit pas être remplacée par des événements fictifs.

## Règle d'intégrité des données

Aucun compteur, événement, timestamp, niveau de sévérité ou état de santé ne doit être présenté comme réel s'il est généré artificiellement. En cas d'échec d'une source, l'interface doit afficher explicitement l'état d'erreur ou l'absence de données.

## Ce que le module fait

- présente une vue informative de données de vulnérabilités publiques lorsqu'elles sont récupérées ;
- indique l'état des sources selon les réponses effectivement observées par le navigateur ;
- permet une actualisation lorsque cette fonction est présente dans le code.

## Ce que le module ne fait pas

- aucune surveillance de l'infrastructure d'un utilisateur ;
- aucune détection locale de malware ;
- aucune modification de système ou de réseau ;
- aucune mitigation automatique ;
- aucun EDR ou antivirus ;
- aucune décision autonome de sécurité.

## Développement local

Pour examiner la page localement, servir le dépôt avec un serveur HTTP statique adapté, par exemple :

```bash
cd public
python3 -m http.server 8000
```

Puis ouvrir `/soc-live.html` sur le serveur local. Cette procédure de développement ne constitue pas une preuve de fonctionnement en production.

## Validation

Une validation doit vérifier le code actuel et l'exécution réelle :

1. les endpoints réellement appelés correspondent aux sources documentées ;
2. aucune donnée fictive n'est injectée en cas d'erreur ;
3. les erreurs réseau et rate limits sont traités sans fausse indication de disponibilité ;
4. les données affichées conservent leur identifiant et leur date lorsqu'ils sont fournis par la source ;
5. les contrôles CI correspondants sont exécutés avec succès.

Un échec d'infrastructure GitHub ne doit pas être requalifié en échec applicatif, et inversement.

## Évolutions réalistes

Les améliorations possibles, à traiter séparément et avec tests, sont :

- cache local explicitement marqué comme tel ;
- filtres par source, sévérité et période ;
- liens vers les avis/CVE sources ;
- gestion visible des quotas et délais ;
- tests automatisés du parsing des réponses API avec fixtures figées ;
- éventuellement un proxy/backend dédié si la fiabilité, la confidentialité ou les limites navigateur justifient cette architecture.

Toute évolution opérationnelle doit être accompagnée de contrôles d'autorisation, d'audit, de rollback et de tests adversariaux avant d'être présentée comme disponible.

**Dernière révision documentaire : septembre 2026.**
