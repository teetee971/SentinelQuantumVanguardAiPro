# Service de sessions vocales Sentinel

Ce composant fournit une capacité de sessions vocales contrôlées pour les surfaces autorisées de Sentinel.

La configuration d’exécution, les interfaces réseau, les mécanismes d’identité, les fournisseurs, les paramètres de session et les procédures de déploiement ne sont pas publiés dans ce document. Ils restent réservés aux espaces opérationnels autorisés.

Principes publics :

- service désactivé par défaut tant que les validations requises ne sont pas terminées ;
- secrets injectés uniquement par l’environnement d’exécution ;
- aucun secret, jeton ou flux audio sensible ne doit être placé dans l’application ou les journaux ;
- durée et portée des sessions limitées ;
- absence de persistance audio annoncée par défaut ;
- contrôle humain et validation d’environnement avant activation.

Cette page ne constitue pas une documentation d’API ni une autorisation d’activation en production.
