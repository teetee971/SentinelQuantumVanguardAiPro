# Phone Core 1.0.6 — refonte appels et messages

Le retour physique sur Samsung S24 / Android 16 décrit un appel lancé depuis Sentinel pendant lequel l’interface indique une absence d’appel, alors que les rôles et autorisations sont accordés. #1441 corrigeait la fiche vide ; ce changement ne résolvait pas le défaut de liaison.

## Interface livrée

- Téléphone : quatre onglets (Clavier, Récents, Contacts, Réglages), clavier tactile, choix de ligne conservé, action Appeler ancrée en bas, accès SMS et copie.
- Appel : état et durée prioritaires, identité compacte, analyse indicative repliable, commandes critiques conservées. L’absence de session est distinguée de l’absence d’appel Android.
- Messages : entrée par les conversations, lignes compactes ouvrant le fil au toucher, actions WhatsApp/suppression dans un menu, bulles entrantes/sortantes distinctes.
- Réponse directement dans le fil : champ ancré au bas de l’écran, choix explicite en multi-SIM, mêmes contrôles de rôle et permissions que le formulaire. Au plus cinq brouillons de 20 000 caractères sont conservés en état sauvegardé local ; ils ne changent pas de destinataire quand on navigue entre les fils.
- Confidentialité et export repliables. Les SMS opérateur ne sont pas présentés comme chiffrés de bout en bout.

## Session Telecom

Le service publie un état atomique primaire/liste/liaison via StateFlow. Un ancien propriétaire ne peut pas effacer l’état du service courant. La reprise de l’interface réconcilie les appels avec la liste du framework sur le thread principal, sans répéter les notifications. L’enregistrement des preuves UI est séparé de la collecte de la session.

Si Android détecte un appel sans session Sentinel, le message décrit un défaut de liaison et propose le retour à l’écran Android et le diagnostic. Aucun numéro ou contrôle n’est inventé. Le diagnostic montre l’état d’appel Android, la liaison du service et les nombres de sessions sans afficher de données personnelles.

## Validation

Tests unitaires sur la propriété de la session et les états absent/inconnu/appel détecté. Contrôles Android existants conservés et adaptés aux onglets natifs. Nouveau parcours Android 16 sur émulateur : vrai raccordement Telecom, appel entrant accepté dans l’interface, appel sortant demandé par Sentinel, fin d’appel, réception SMS synthétique et réponse dans le fil. Captures CI limitées aux données synthétiques.

Ces tests virtuels ne certifient pas le modem du S24. Le défaut d’appel rapporté exige un nouveau test physique sur cet appareil avec cette version, ainsi que les scénarios audio, verrouillage, double appel et SIM/opérateur. Le statut 100 % fonctionnel n’est pas attribué.
