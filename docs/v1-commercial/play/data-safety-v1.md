# Data safety · Sentinel Phone Core V1

**Brouillon au 18 novembre 2026. Non soumis.** Réponses proposées pour le formulaire Data safety de l’AAB `com.sentinel.quantum`, versionCode 10, construit avec `-PsentinelCommercialV1=true`. La définition utilisée est celle de l’aide Play : collecter signifie transmettre une donnée hors du téléphone, y compris par une bibliothèque. Le traitement seulement local n’est pas une collecte. [Data safety](https://support.google.com/googleplay/android-developer/answer/10787469).

Décision produit du 18 novembre 2026 : lancement public gratuit, sans abonnement, sans achat intégré, sans paiement dans l’application, sans publicité et sans compte. Aucune société ni entreprise individuelle immatriculée n’est déclarée. Cette décision ne vaut pas conformité acquise.

Ce brouillon ne reprend pas `docs/PLAY-DATA-SAFETY.md`. Ce fichier-là décrit le dépôt entier, dont le Studio voix et les appels VoIP, exclus de V1.

## Questions générales

| Question du formulaire | Réponse proposée | Pourquoi |
| --- | --- | --- |
| L’application collecte-t-elle ou partage-t-elle des types de données du formulaire ? | Oui | Deux transmissions sortent du téléphone : les appels, SMS et MMS remis à l’opérateur, et le téléchargement HTTPS des catalogues. |
| Toutes les données collectées sont-elles chiffrées en transit ? | Non | Les catalogues sont en HTTPS. Les SMS et la voix passent par l’opérateur. L’application n’ajoute pas de chiffrement de bout en bout. Répondre Oui serait faux. |
| Les personnes peuvent-elles demander la suppression des données collectées ? | Oui, pour les données détenues sur le téléphone par l’application. Non pour un compte, qui n’existe pas. | Historique de filtrage, journal de diagnostic, préférences et désinstallation. L’éditeur ne détient pas de copie serveur du répertoire, des SMS ou du journal. [[À COMPLÉTER : conservation réelle des journaux d’hébergement des catalogues, et moyen de les effacer.]] |
| L’application permet-elle de créer un compte ? | Non | Décision produit du 18 novembre 2026. Le graphe V1 n’ouvre pas `SettingsScreen` ni Auth0. Les activités Auth0 sont retirées du manifeste. Le code Auth0 reste compilé : le reconfirmer sur l’AAB signé. L’URL de suppression de compte n’est pas exigée tant qu’aucun compte n’existe. [Suppression de compte](https://support.google.com/googleplay/android-developer/answer/13327111). |
| L’application contient-elle des annonces ? | Non | Aucun SDK `play-services-ads` ni autre SDK publicitaire dans les fichiers Gradle du module Android. Aucun écran publicitaire dans le graphe V1. [Déclaration Annonces](https://support.google.com/googleplay/android-developer/answer/9859455). |
| Fonctions financières | L’application ne fournit aucune fonction financière | Pas de bibliothèque Billing, pas d’achat, pas de portefeuille. Les SMS ne servent pas au suivi budgétaire ni au paiement. [Déclaration Financial features](https://support.google.com/googleplay/android-developer/answer/13849271). |

## Types à déclarer comme collectés

### Numéro de téléphone

- Collecté : oui. Partagé : non, au sens Play, si l’opérateur est traité comme le réseau qui achemine l’appel ou le message demandé, et si aucun serveur de l’éditeur ne reçoit le numéro.
- Éphémère côté éditeur : oui, seulement si l’éditeur confirme qu’il ne conserve aucune copie. Le formulaire demande quand même de le déclarer. L’opérateur peut conserver ses propres traces : l’éditeur ne les contrôle pas.
- Facultatif : oui. L’accueil s’ouvre sans rôle. Le numéro sort du téléphone quand la personne passe un appel ou envoie un message.
- Finalité : fonctionnalité de l’application. Pas d’analyse d’audience, de publicité, de personnalisation ni de gestion de compte.
- Ne pas déclarer le numéro comme identifiant publicitaire.

### SMS ou MMS

- Collecté : oui, pour le message que la personne envoie et pour le MMS téléchargé via l’API opérateur. Le message reçu, lu ensuite dans le fournisseur Android et non renvoyé, reste un traitement local.
- Partagé : non vers l’éditeur. L’export de 100 SMS n’est pas un partage à déclarer s’il reste une action choisie, avec confirmation, vers l’application que la personne désigne. Le décrire dans la politique reste obligatoire. [User Data](https://support.google.com/googleplay/android-developer/answer/10144311).
- Éphémère côté éditeur : oui, sous la même réserve que le numéro. Le fournisseur Android, lui, conserve le message sur le téléphone.
- Facultatif : oui. Sans rôle SMS, l’envoi et la lecture sont fermés.
- Finalité : fonctionnalité de l’application.
- Chiffrement : ne pas déclarer les SMS comme chiffrés en transit par Sentinel.

### Identifiants d’appareil ou autres identifiants

- Collecté : oui pour l’adresse IP vue par l’hôte lors du GET des catalogues. La requête ne contient pas le numéro recherché. L’en-tête ajouté par le code est `Accept: application/json`. OkHttp ajoute son en-tête technique habituel.
- Adresses : `https://sentinelquantumvanguardaipro.pages.dev/public/data/arcep-numbering.json` et `https://sentinelquantumvanguardaipro.pages.dev/public/data/rtr-numbering.json`.
- Partagé : non, si l’hébergeur n’est qu’un sous-traitant qui délivre le fichier pour l’éditeur. [[À COMPLÉTER : nom du sous-traitant, instruction de traitement, et absence de revente.]]
- Éphémère : [[À COMPLÉTER : oui seulement si les journaux ne sont pas conservés au-delà de la requête. Sinon, non.]]
- Facultatif : oui pour l’actualisation périodique, qui est un interrupteur désactivé par défaut. La recherche manuelle peut télécharger sans cet interrupteur. Tant que ce chemin existe, ne pas présenter la collecte comme entièrement coupée par le réglage. Voir le blocage dans les divulgations.
- Finalité : fonctionnalité de l’application.
- Ne pas déclarer une position approximative déduite de l’adresse IP : le code ne fait pas cette déduction. Si l’hébergeur le fait de son côté, l’éditeur doit le déclarer. [[À COMPLÉTER : confirmer l’absence d’usage de l’IP comme position.]]

## Types à ne pas cocher comme collectés

Ces données sont lues ou écrites sur le téléphone et ne sont pas transmises à l’éditeur dans le profil V1.

| Type Play | Traitement V1 |
| --- | --- |
| Contacts | Lecture locale du nom et de l’organisation après autorisation. Pas d’envoi. |
| Journal d’appels, au-delà du numéro remis à l’opérateur pour l’appel en cours | Lecture locale des récents après le rôle Téléphone. Pas d’envoi du journal. |
| SMS stockés, hors message en cours d’envoi | Lecture locale après le rôle SMS. |
| Photos, vidéos, fichiers audio, micro | `RECORD_AUDIO` et `CAMERA` sont retirés du manifeste V1. Pas d’enregistrement d’appel. |
| Position | Les permissions de position, Wi-Fi et Bluetooth sont retirées. |
| Journaux de plantage, diagnostics de performance, identifiant publicitaire | Aucun SDK Firebase, Crashlytics ou analytics n’est déclaré dans les dépendances directes de `app/build.gradle`. |
| E-mails, applications installées, historique de navigation | Le partage `ACTION_SEND` texte est retiré de l’activité principale V1. Pas d’inventaire d’applications. |
| Informations financières, santé | Absentes du parcours V1. |

Le code des bibliothèques LiveKit, Auth0, WireGuard et Play Services Wearable reste dans le graphe Gradle. Le manifeste V1 retire les composants Auth0, LiveKit, WireGuard et Wear qui étaient connus. Cette revue n’a pas reconstruit l’AAB ni capturé le trafic. Avant de répondre « non » pour les journaux de plantage et les identifiants, relancer `audit/v1-commercial/validation/check_merged_manifest.py` sur le manifeste fusionné de l’AAB signé et observer le réseau au premier lancement.

## Pratiques de sécurité à ne pas survendre

- Sauvegarde cloud de l’application : désactivée (`allowBackup=false` et règles d’extraction qui excluent tout le domaine).
- HTTP en clair : refusé. L’exception de réseau local du profil IA est retirée du manifeste V1.
- Empreintes de numéros : HMAC-SHA256, clé non exportable du coffre Android.
- Base des décisions de filtrage : fichier privé, sans numéro en clair, sans couche de chiffrement supplémentaire identifiée.
- SMS de l’opérateur : non chiffrés de bout en bout par l’application.

## Données exclues, à ne pas déclarer comme fonctions V1

Ne pas déclarer de collecte pour Qwen, un modèle local IA, le VPN, le Mesh, Wear, le Studio voix, un scan radio, la veille OSINT, la réputation d’appelant distante ou un compte Auth0. Ces chemins sont coupés ou masqués dans le profil. Les déclarer créerait une fiche fausse dans l’autre sens.
