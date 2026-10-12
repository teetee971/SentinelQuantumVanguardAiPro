# Service d’enrichissement téléphonique Sentinel

Ce composant fournit un enrichissement défensif et borné pour l’analyse locale des appels. Il reste facultatif : le filtrage principal doit continuer à fonctionner localement sur l’appareil autorisé.

La configuration d’exécution, les URL, les routes, les noms de fournisseurs, les paramètres de stockage, les clés d’authentification et les procédures de déploiement ne sont pas publiés dans ce document. Ces informations restent réservées aux espaces opérationnels autorisés.

Principes publics :

- aucun numéro brut ne doit être conservé par défaut ;
- les entrées et les quotas sont bornés ;
- les signalements ne sont pas des verdicts d’identité ou de fraude ;
- les fonctions communautaires nécessitent une modération et des contrôles d’abus ;
- une indisponibilité distante doit dégrader proprement le service local ;
- les secrets restent côté serveur et ne sont jamais embarqués dans une application publique ;
- une validation ponctuelle ne constitue ni SLA ni disponibilité permanente.

Cette page ne constitue pas une documentation d’API ni une autorisation d’activation en production.
