# Voice — limites séparées de Phone Core

Aucune nouvelle sonde ou modification de service n'a été exécutée dans cette qualification du 30 octobre. Aucun secret modifié, déploiement, changement de plan ou dépense déclenché.

Le diagnostic conservé rapporte un endpoint live200 et un endpoint ready503, avec des prérequis d'authentification/LiveKit/Redis à vérifier. La cause exacte du 503 n'est pas prouvée ; ces anciennes observations ne déclarent pas la disponibilité actuelle. L'état vert Render/live n'établit pas qu'une session PTT est opérationnelle.

PTT est un parcours réseau distinct des appels SIM Android. Transformation vocale en appel mobile SIM : NON PRISE EN CHARGE, pas un réglage utilisateur oublié. Aperçu audio local et client PTT présents ne suffisent pas à qualifier une session bi-appareil. Les essais matériels/audio et authentifiés restent NON TESTÉS et conditionnés aux ressources autorisées ; aucune clé privée ni jeton à partager dans les preuves.
