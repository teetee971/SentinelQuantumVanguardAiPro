# Data safety · Sentinel Phone Core V1

**Brouillon au 18 novembre 2026. Non soumis.** Réponses proposées pour le formulaire Data safety de l’AAB `com.sentinel.quantum`, versionCode 10, construit avec `-PsentinelCommercialV1=true`. La définition utilisée est celle de l’aide Play : collecter signifie transmettre une donnée hors du téléphone, y compris par une bibliothèque. Le traitement seulement local n’est pas une collecte. [Data safety](https://support.google.com/googleplay/android-developer/answer/10787469).

Décision produit du 18 novembre 2026 : lancement public gratuit, sans abonnement, sans achat intégré, sans paiement dans l’application, sans publicité et sans compte. Aucune société ni entreprise individuelle immatriculée n’est déclarée. Cette décision ne vaut pas conformité acquise.

Ce brouillon ne reprend pas `docs/PLAY-DATA-SAFETY.md`. Ce fichier-là décrit le dépôt entier, dont le Studio voix et les appels VoIP, exclus de V1.

## Questions générales

| Question du formulaire | Réponse proposée | Pourquoi |
| --- | --- | --- |
| L’application collecte-t-elle ou partage-t-elle des types de données du formulaire ? | Non démontré pour les appels et les SMS. Ouvert pour les catalogues. | Le texte d’aide dit que collecter signifie transmettre une donnée hors du téléphone. Il excepte le traitement qui reste sur l’appareil. Aucun code V1 relu n’envoie le corps d’un SMS, le journal ou l’audio d’un appel à un serveur Sentinel. Le transport opérateur n’est pas, à lui seul, une collecte par le développeur. Le GET des catalogues, lui, sort du téléphone. |
| Toutes les données collectées sont-elles chiffrées en transit ? | Ne pas répondre tant que le périmètre de collecte n’est pas figé. | Si la seule transmission retenue est le GET HTTPS des catalogues, le chiffrement en transit de cette requête est HTTPS. Cela ne chiffre pas les SMS ni la voix de l’opérateur. |
| Les personnes peuvent-elles demander la suppression des données collectées ? | Oui, pour les données détenues sur le téléphone par l’application. Non pour un compte, qui n’existe pas. | Historique de filtrage, journal de diagnostic, préférences et désinstallation. L’éditeur ne détient pas de copie serveur du répertoire, des SMS ou du journal. [[À COMPLÉTER : conservation réelle des journaux d’hébergement des catalogues, et moyen de les effacer.]] |
| L’application permet-elle de créer un compte ? | Non | Décision produit du 18 novembre 2026. Le graphe V1 n’ouvre pas `SettingsScreen` ni Auth0. Les activités Auth0 sont retirées du manifeste. Le code Auth0 reste compilé : le reconfirmer sur l’AAB signé. L’URL de suppression de compte n’est pas exigée tant qu’aucun compte n’existe. [Suppression de compte](https://support.google.com/googleplay/android-developer/answer/13327111). |
| L’application contient-elle des annonces ? | Non | Aucun SDK `play-services-ads` ni autre SDK publicitaire dans les fichiers Gradle du module Android. Aucun écran publicitaire dans le graphe V1. [Déclaration Annonces](https://support.google.com/googleplay/android-developer/answer/9859455). |
| Fonctions financières | L’application ne fournit aucune fonction financière | Pas de bibliothèque Billing, pas d’achat, pas de portefeuille. Les SMS ne servent pas au suivi budgétaire ni au paiement. [Déclaration Financial features](https://support.google.com/googleplay/android-developer/answer/13849271). |

## Correction du 18 novembre 2026 au soir

Une version précédente de cette feuille classait le numéro, les SMS et les MMS comme collectés parce que l’opérateur les achemine. Cette classification est retirée. Elle dépassait la preuve : le code V1 relu ne transmet pas ces contenus à un serveur du développeur. Le transport de l’opérateur reste le transport de l’opérateur.

## Types à ne pas déclarer comme collectés par le développeur, sur la preuve actuelle

### Numéro de téléphone, SMS et MMS

- Lecture et envoi : locaux, ou remis à Android puis à l’opérateur.
- Aucune requête relue ne place le numéro ou le texte dans un URL ou un corps envoyé à `pages.dev` ou à `onrender.com` lorsque le profil V1 est actif. La réputation distante est coupée par `COMMERCIAL_PHONE_CORE_V1`.
- L’export de 100 SMS est une action choisie, avec confirmation, vers l’application que la personne désigne. Le décrire dans la politique. Ne pas le déclarer comme un partage vers le développeur. [User Data](https://support.google.com/googleplay/android-developer/answer/10144311).
- Ne pas répondre « chiffré de bout en bout » pour les SMS ou la voix.

### Identifiants d’appareil

Le client des catalogues ne lit pas l’adresse IP et ne l’écrit pas dans la requête. La requête est un GET sans paramètre de numéro. L’hôte voit néanmoins l’adresse IP de la connexion. La durée de conservation de ces journaux n’est pas connue. Ne pas cocher « identifiant d’appareil collecté » tant que ce point n’est pas établi. Ne pas cocher non plus « aucune donnée ne sort » : le GET a lieu.

Les URL concernées sont `https://sentinelquantumvanguardaipro.pages.dev/public/data/arcep-numbering.json` et `https://sentinelquantumvanguardaipro.pages.dev/public/data/rtr-numbering.json`. L’en-tête ajouté par le code est `Accept: application/json`. [[À COMPLÉTER : conservation des journaux de l’hôte, et usage ou non de l’adresse IP comme position.]]

L’interrupteur d’actualisation ne coupe pas le GET déclenché par la fiche d’appel ni par la recherche du composeur. Voir l’audit contradictoire.

## Types à ne pas cocher comme collectés

Ces données sont lues ou écrites sur le téléphone et ne sont pas transmises à l’éditeur dans le profil V1.

| Type Play | Traitement V1 |
| --- | --- |
| Contacts | Lecture locale du nom et de l’organisation après autorisation. Pas d’envoi. |
| Journal d’appels | Lecture locale des récents après le rôle Téléphone. Pas d’envoi du journal à un serveur Sentinel. |
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
