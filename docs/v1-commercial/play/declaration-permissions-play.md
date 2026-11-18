# Déclaration de permissions Play · Sentinel Phone Core V1

**Brouillon au 18 novembre 2026. Non soumis.** À saisir dans le Permissions Declaration Form de la Play Console pour l’AAB `com.sentinel.quantum`, versionCode 10, versionName `1.0.9`, construit avec `-PsentinelCommercialV1=true`.

Google demande de déclarer chaque permission SMS ou Call Log, puis de redéposer le formulaire si l’usage change. Les libellés exacts des champs n’ont pas été relus dans une Console ouverte : l’accès Play est indisponible. Les réponses suivent les rubriques publiques : fonctionnalité essentielle, justification, parcours utilisateur, démonstration vidéo. [Politique SMS et Call Log](https://support.google.com/googleplay/android-developer/answer/10208820).

## Cas d’usage à cocher

| Permission déclarée dans le manifeste V1 | Cas à déclarer | Cas à ne pas cocher |
| --- | --- | --- |
| `READ_SMS`, `RECEIVE_SMS`, `SEND_SMS`, `RECEIVE_MMS`, `RECEIVE_WAP_PUSH` | Gestionnaire SMS par défaut | Anti-hameçonnage SMS, sauvegarde, Caller ID hors gestionnaire, vérification de compte, transaction financière |
| `READ_CALL_LOG` | Gestionnaire Téléphone par défaut | Exception Caller ID / spam, enregistrement d’appels, vérification de compte par appel |

`WRITE_SMS`, `WRITE_CALL_LOG` et `PROCESS_OUTGOING_CALLS` ne sont pas déclarées. Ne pas les ajouter au formulaire.

Le filtrage local utilise `CallScreeningService` et, dès Android 10, `ROLE_CALL_SCREENING`. Ce rôle ne justifie pas `READ_CALL_LOG`. Sur Android 7 à 9, le code propose le filtrage via l’application Téléphone par défaut, parce que `ROLE_CALL_SCREENING` n’existe pas. La vidéo de revue doit montrer le parcours de l’appareil cible. La recette prévue est un Samsung Galaxy S24+, donc le parcours Android 10+ avec les trois rôles distincts.

L’application demande le rôle avant la permission restreinte correspondante. Elle relit le rôle avant d’utiliser la permission. Ce comportement est celui lu dans l’assistant, `SmsActivationUiModel` et `SystemCallLogReader`. Il reste à le filmer sur l’appareil.

## Fonctionnalité essentielle commune

Sentinel Phone Core sert à passer et recevoir des appels, à envoyer et recevoir des SMS et MMS, et à appliquer des règles locales de filtrage. Sans le rôle Téléphone, le composeur ne place pas l’appel. Sans le rôle SMS, l’envoi et la lecture des conversations restent fermés. La fiche Play doit présenter ces fonctions comme le cœur de l’application, et ne doit pas présenter Qwen, le VPN, le Mesh, les scanners radio, le Studio voix ni la réputation distante.

## Réponses par permission

### `READ_SMS`

- Fonctionnalité essentielle : afficher les conversations SMS de la personne lorsque Sentinel est l’application SMS par défaut.
- Justification : le fournisseur SMS d’Android n’est lisible pour cette fonction qu’avec cette permission et le rôle SMS. La lecture alimente la liste des conversations sur le téléphone. Elle alimente aussi l’export, limité à 100 SMS, seulement après une confirmation et un partage choisi par la personne.
- Parcours : Accueil, assistant d’activation, « Choisir Sentinel pour les SMS », dialogue système, puis « Autoriser uniquement les permissions nécessaires ». Sans le rôle, ce bouton de permission n’est pas proposé.
- Vidéo : montrer le refus du rôle, l’absence de lecture, puis l’acceptation, l’affichage d’une conversation de test, et le retrait du rôle dans les réglages Android avec l’arrêt des lectures suivantes.

### `RECEIVE_SMS`

- Fonctionnalité essentielle : recevoir les SMS entrants en tant qu’application SMS par défaut.
- Justification : le récepteur `SMS_DELIVER` est le chemin de livraison réservé au gestionnaire SMS. Le message est enregistré par la pile Android. Sentinel ne le retransmet pas à son serveur.
- Parcours : même assistant, après le rôle SMS. Le texte de l’assistant indique que la réception exige l’autorisation Android.
- Vidéo : envoyer un SMS de test vers la ligne, montrer la conversation, puis retirer le rôle et montrer qu’une nouvelle livraison n’est plus traitée par Sentinel.

### `SEND_SMS`

- Fonctionnalité essentielle : envoyer un SMS depuis le composeur, sur la SIM choisie.
- Justification : l’envoi passe par `SmsManager` seulement si le rôle SMS est détenu et si la permission effective est accordée. La ligne est choisie parmi les abonnements actifs. Aucune ligne n’est devinée si l’état téléphonique est indisponible.
- Parcours : après le rôle, l’assistant demande les permissions d’envoi et de détection de ligne avant les permissions de boîte de réception.
- Vidéo : choisir la SIM sur un appareil double SIM si disponible, envoyer un SMS vers un numéro de test, montrer l’accusé local, puis refuser la permission et montrer le refus d’envoi.

### `RECEIVE_MMS` et `RECEIVE_WAP_PUSH`

- Fonctionnalité essentielle : recevoir un MMS et son avis WAP Push lorsque Sentinel est l’application SMS par défaut.
- Justification : Android livre l’avis au gestionnaire par `WAP_PUSH_DELIVER`. Le téléchargement du MMS utilise l’API opérateur et un abonnement valide. Le décodeur n’accepte qu’un aperçu borné. Le contenu non retenu reste en quarantaine locale. Ces permissions ne sont demandées qu’une fois le rôle SMS détenu.
- Parcours : carte « Réception MMS » de l’assistant, bouton « Autoriser la réception MMS » seulement si le rôle est déjà accordé.
- Vidéo : recevoir un MMS de test de petite taille, montrer l’aperçu, puis un contenu hors format qui reste en quarantaine. Montrer le retrait du rôle pendant un traitement et l’absence de nouvel effet non autorisé. Prévoir une prise distincte si une seule SIM est disponible.

### `READ_CALL_LOG`

- Fonctionnalité essentielle : afficher les appels récents dans le composeur lorsque Sentinel est l’application Téléphone par défaut.
- Justification : la liste des récents est lue dans le journal Android, avec un plafond de 500 lignes affichées et un parcours de lecture borné. Le rôle Téléphone est revérifié avant la requête. Le filtrage d’appels n’utilise pas cette permission. Les données lues ne sont pas envoyées à l’éditeur. L’historique de filtrage, distinct, ne stocke qu’une empreinte HMAC.
- Parcours : assistant, « Choisir Sentinel comme téléphone », dialogue de rôle, puis « Autoriser les données locales ». La demande de journal n’est ajoutée que si le rôle Téléphone est déjà détenu. Les contacts peuvent être demandés à part.
- Vidéo : refuser le rôle et montrer l’absence d’historique, accepter le rôle puis la permission et montrer des appels de test, retirer le rôle dans Android et montrer que les récents ne sont plus relus. Ne pas présenter un enregistrement audio.

## Instructions pour la revue Google

Texte proposé pour le champ d’accès revue :

1. Installer l’AAB de revue `com.sentinel.quantum`, versionCode 10. Ne pas utiliser le paquet debug `com.sentinel.quantum.v1validation` comme build de production.
2. Ouvrir Sentinel Phone Core, puis « Ouvrir l’assistant d’activation ».
3. Pour les SMS : accorder le rôle SMS, puis les permissions affichées. Une SIM de test et un second téléphone sont nécessaires.
4. Pour le journal : accorder le rôle Téléphone, puis l’autorisation d’historique. Le filtrage s’active par un bouton séparé, « Activer le filtrage ».
5. Les numéros, SMS et contacts de démonstration doivent être des données de test fournies par l’éditeur. [[À COMPLÉTER : identifiants de l’appareil de revue, ligne de test, et personne joignable.]]

Aucune exception d’ancien APK n’est demandée. Le champ d’exception d’APK antérieur à 2019 reste vide.

## Permissions hors de ce formulaire

Ces permissions sont dans le manifeste V1. Les pages lues ne décrivent pas un second formulaire identique pour chacune. Les signaler si la Console les demande.

| Permission | Rôle dans V1 |
| --- | --- |
| `CALL_PHONE` | Placer l’appel après le rôle Téléphone et l’autorisation. Un simple `ACTION_DIAL` ne suffit pas au gestionnaire qui doit lancer l’appel. |
| `READ_PHONE_STATE` | Lister les lignes SIM pour l’appel et l’envoi. Demandée après le rôle concerné. Ce n’est pas une permission du groupe Call Log dans la politique lue. |
| `READ_CONTACTS` | Afficher le nom et l’organisation d’un contact. Refusable. Le reste de l’application continue. |
| `POST_NOTIFICATIONS` | Notifications d’appel et de message. L’aperçu SMS est un réglage séparé, désactivé par défaut. |
| `USE_FULL_SCREEN_INTENT` | Interface d’appel entrant, y compris écran verrouillé. La politique des permissions sensibles range la réception d’appels parmi les usages qui peuvent recevoir cette autorisation. [Politique du programme](https://support.google.com/googleplay/android-developer/answer/18265346). |
| `INTERNET`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK` | Téléchargement HTTPS des catalogues publics et connectivité. Pas d’accès SMS ni journal. |

## Plan de vidéo unique

Durée visée : quelques minutes, données de test seulement.

1. Écran d’accueil Sentinel Phone Core et numéro de version.
2. Assistant sans aucun rôle.
3. Attribution du rôle Téléphone, puis permission d’appel, d’état de ligne et de journal. Appel sortant et appel entrant de test. Raccrochage.
4. Activation séparée du filtrage, règle locale sur un numéro de test, appel montré comme filtré, puis retrait du rôle de filtrage.
5. Attribution du rôle SMS, permissions, envoi, réception, MMS court, export annulé puis export confirmé vers un partage annulé.
6. Paramètres : aperçu SMS désactivé, actualisation des catalogues désactivée, lien de politique. Après publication, le lien doit ouvrir la politique V1 et non la politique de la vitrine.
7. Réglages Android : retrait des rôles et retour à l’application Téléphone et à l’application SMS d’origine.

Ne pas filmer Qwen, un VPN, un scan radio, ni un compte.
